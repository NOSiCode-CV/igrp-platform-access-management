# Requirements — SSO Session Link and Logout Cascade

**Feature:** link every app session created under one AM sign-in, and make RP-initiated logout (`/connect/logout`) revoke all of them instead of only the app that logged out.
**Status:** draft, not planned.
**Target branch:** `development`.
**Interacts with:** the framework spec `igrp-framework-nextjs/docs/superpowers/specs/2026-10-22-multi-app-auth-sessions-spec.md` (how the apps notice a revoked session) and the AM client spec `frontend/packages/packages/client/docs/specs/2026-10-22-session-client-alignment.md` (`checkSession()`). Builds on commits `0444154d` (several apps per device) and `6674a9d6` (drops the `ux_session_user_device_active` index that `0444154d` alone turned into a login failure).

---

## 0. Problem

Several IGRP apps run on one host: an Application Center at `/` plus apps under `/apps/*`. Each app runs its own authorization-code exchange, so each sign-in opens its own `t_user_session` row. The second and later apps sign in silently (`prompt=none`) on the AM browser session the first one created.

Logout does not follow that shape:

- `SessionLogoutHandler.revokeBoundSession` revokes only the row named by the ID token's `igrp_session_id`.
- `removeAuthorization` removes only that app's `OAuth2Authorization`.
- Every sibling row stays `ACTIVE`. The refresh grant is a back-channel call authenticated by client credentials and never consults the browser session, so invalidating `JSESSIONID` does not reach it either. Those apps keep calling the API and refreshing tokens.
- In practice they run to the absolute limit (`absoluteTimeoutSeconds`, 28800 s by default, from their own sign-in). Each successful refresh slides the idle deadline forward (`applyRefresh`), so an app in use never idles out.
- Cascading to the upstream IdP's `end_session_endpoint` does not help: AM's refresh tokens are AM's own and do not check upstream liveness.

From the user's side, "sign out" signs them out of one app, and every other app in use stays signed in for up to 8 hours.

Nothing records which rows came from the same browser sign-in. `device_id` cannot do it. It hashes `(User-Agent, IP, client_id)`, and for server-side token exchanges those are the app server's values, not the browser's (see the comment in `SessionIssuanceService.openNewSession`).

---

## 1. Functional requirements

### 1.1 SSO session key

- **R1.1** `t_user_session` gains a nullable column `sso_session_key VARCHAR(64)`, with an index on `(user_id, sso_session_key, status)`.
- **R1.2** The key identifies one AM sign-in: one successful browser authentication at AM, local or federated. Every session opened by an authorization-code grant whose authorization was granted under that sign-in carries the same key. A different sign-in (another browser, or the same browser after logout) yields a different key.
- **R1.3** The key is stored hashed: SHA-256, base64url, no padding. It is never a raw servlet session id or any other value that would authenticate a request if leaked.
- **R1.4** `SessionIssuanceService.openNewSession` stores the key. The refresh path (`applyRefresh`) never changes it.
- **R1.5** Grants with no browser sign-in behind them get no key. That covers `client_credentials`, and a refresh grant that falls through to `openNewSession` without a recoverable key. Rows without a key behave exactly as today.

### 1.2 Key source

The key must be available at `/oauth2/token`. That call is server-to-server and carries no browser cookie, so the value has to travel with the stored authorization.

- **R1.6** A random login id (128 bits, CSPRNG) is generated in the AM login success path, for both form login and federated login. It is placed in the authentication's details or attributes so it is persisted with the authorization, and `JwtTokenConfig.igrpTokenCustomizer` reads it from `context.getPrincipal()`.
- **Rejected: the servlet session id** (`WebAuthenticationDetails.getSessionId()`). Whether it survives the JDBC serialization of the principal is beside the point. It ties the key to the servlet session, which makes R3.2 a correctness requirement for logout, not only for `prompt=none`. On several replicas without a shared session store, apps that authorized against different pods get different keys and the cascade becomes partial: some apps stay signed in after logout, non-deterministically. A login id persisted with the authorization is immune to that.

### 1.3 Logout cascade

- **R2.1** In `SessionLogoutHandler`, after the bound session is resolved from `igrp_session_id`:
  - if it has an `sso_session_key`, revoke every `ACTIVE` row with the same `user_id` and key;
  - if it has none, revoke only the bound row, as today.
- **R2.2** Each revoked row gets reason `USER_LOGOUT`, closed by `USER`, and the same side effects `revokeBoundSession` applies today: heartbeat eviction, `evictBySubject`, `SessionAuditLogger.recordRevoked`, and one `SessionRevokedEvent` per row.
- **R2.3** Sibling apps' `OAuth2Authorization` rows are not removed. A revoked row is already enough:
  - `applyRefresh` rejects a non-`ACTIVE` session with `invalid_grant` (`session_not_active`);
  - `SessionEnforcementFilter` answers `401 session_revoked` for its access tokens;
  - `/api/session/check` reports `valid=false` with reason `USER_LOGOUT`.
- **R2.4** The cascade runs in its own transaction (`REQUIRES_NEW`), after the bound row's revocation. A failure to revoke siblings must not roll back the bound row's revocation, or stop the servlet-session invalidation or the upstream IdP cascade. It is logged at WARN with the key fingerprint and the count.
- **R2.5** `/oauth2/revoke`, `/api/session/close` and the admin kill endpoints keep their per-session meaning. Only RP-initiated logout cascades.

### 1.4 Configuration

- **R3.1** Set `server.servlet.session.timeout` explicitly, via an env override. It is the window in which `prompt=none` signs a user into another app without a prompt; today it is Spring's unconfigured 30-minute default. The value is a product decision. Document it next to `igrp.session.*`.
- **R3.2** When AM runs more than one replica without sticky sessions, the servlet session must be shared (Spring Session on the existing Redis). Otherwise `prompt=none` succeeds or fails depending on which pod answers. Document it as a deployment requirement if not implemented. The cascade does not depend on it (R1.6).
- **R3.3** Document that `IGRP_SESSION_MAX_PER_USER` must be at least (apps per host × devices per user). Each app sign-in is one session against the cap, and LRU eviction signs the oldest app out.

---

## 2. Non-functional requirements

- **N1** Logout latency: the cascade is one indexed `UPDATE … WHERE user_id = ? AND sso_session_key = ? AND status = 'ACTIVE'` (or an equivalent select-then-save), plus cache evictions. No per-row network call.
- **N2** Backwards compatible. Rows created before the migration have no key and keep today's single-row behaviour. No backfill.
- **N3** No raw session identifiers in logs. Log the key's first 8 characters at most.

---

## 3. Design decisions (locked)

- **D1** The link is "same AM sign-in", not "same device" or "same user". Same-user would sign the user out of their other browsers, and same-device is not trustworthy (see Problem).
- **D2** No OIDC back-channel or front-channel logout. Apps learn of the cascade through the mechanisms they already have: a 401 on their next API call, `invalid_grant` on refresh, and `/api/session/check` on tab focus (framework spec).
- **D3** Revoke rows; do not remove sibling authorizations (R2.3). It keeps the cascade to one table and reuses the existing rejection paths.

---

## 4. Non-goals

- Signing out other users' sessions when a different user signs in on the same browser. That needs a trustworthy per-browser id; the comment in `openNewSession` already parks it.
- Changing `/oauth2/revoke` semantics.
- Fixing the cross-site `/connect/logout` POST, where `JSESSIONID` (`SameSite=Lax`) is not sent and the servlet session survives logout. It affects deployments where the app and AM are on different sites, which includes the usual local setup (`localhost` against a remote AM). It is documented on the framework side.
  - This is the worst failure mode of the feature, not a cosmetic gap. Without the session cookie AM cannot resolve the sign-in, so **nothing cascades and the servlet session survives**: the user asks to sign out of everything and stays signed in everywhere. Anyone testing the cascade locally against a remote AM will see it fail; test it same-site.

---

## 5. Validation

- **V1** Two clients sign in silently under one browser sign-in. One logs out. Both rows become `REVOKED` / `USER_LOGOUT`, and the other client's next refresh gets `invalid_grant`.
- **V2** The same user is signed in from two browsers. Logout in one leaves the other browser's rows `ACTIVE`.
- **V3** A row without a key (created before the migration) is revoked alone.
- **V4** The key is identical for two authorization-code grants under one sign-in, and different after logout and a fresh sign-in, for both form and federated login.
- **V5** `client_credentials` tokens get no key and are unaffected by any logout.
- **V6** `/api/session/check` with a cascaded-revoked token returns `valid=false`, `reason=USER_LOGOUT`.
- **V7** With `IGRP_SESSION_MAX_PER_USER` set below the number of apps signed in under one sign-in, the LRU-evicted sibling's `/api/session/check` returns `reason=SESSION_LIMIT_EXCEEDED`, not `USER_LOGOUT`. Apps must be able to tell a cap eviction from a logout.

---

## 6. Open questions

1. What does `GET /api/users/me/applications/{code}/menus` return for an application the user is not assigned to: 403 or an empty list? The framework spec changes 403 handling and needs the answer.
2. How many replicas does the deployed AM run, and with sticky sessions? This decides R3.2.
3. Does the deployed AM include `6674a9d6` (which implies `0444154d`)? `0444154d` alone causes the 2026-10-16 incident: the partial unique index `ux_session_user_device_active` still rejects the second app's session and the sign-in ends in a bare 401. Without either commit, a second app's sign-in closes the first app's session and the cascade question is moot. As of 2026-10-23 both are on `development`, but `staging` has only `0444154d`.
