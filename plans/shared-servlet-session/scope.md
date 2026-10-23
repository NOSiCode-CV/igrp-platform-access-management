# Scope — Shared servlet session across replicas

**Why:** AM may scale past one replica on demand (Kubernetes). The servlet session is in-JVM today, so anything stored in it is lost when a request lands on a different pod, or on the same pod after a restart.
**Status:** scoped, not planned.
**Target branch:** `development`.
**Required by:** `plans/sso-session-logout-cascade/requirements.md` R3.2 — silent sign-in (`prompt=none`) depends on this. The logout cascade itself does not (R1.6).
**Standalone value:** this fixes a live production failure regardless of whether the cascade is ever built.

---

## 0. The failure it fixes

Login fails with **`authorization_request_not_found`** — rendered to users as "Login could not be completed". Reported from `api-gateway-test`.

Mechanism: no custom `AuthorizationRequestRepository` is registered, so Spring's default `HttpSessionOAuth2AuthorizationRequestRepository` stores the pending authorization request in the servlet session. The browser starts at `/oauth2/authorize` on one pod; the IdP redirects back to `/login/oauth2/code/external-idp` on another pod, or the same pod after a restart; the `state` matches no saved request.

A single replica does not make this safe — a rolling deploy or an OOM-kill reproduces it for anyone mid-login. Scaling makes it routine rather than occasional.

`prompt=none` multiplies the exposure: every additional app on the host is another authorize round-trip through AM.

**Not related** to `t_user_session` / `igrp.session.*`. That is AM's own session model, already in Postgres with a Redis cache, and is unaffected by this work. The two are easy to confuse and are not the same thing.

---

## 1. What is already in place

| | |
|---|---|
| `spring-boot-starter-data-redis` | present in `pom.xml` |
| Redis connection | `spring.data.redis.{host,port,password}`, already required at startup |
| Redis serialization precedent | `CacheConfig` uses `GenericJackson2JsonRedisSerializer` |
| **Security-object JSON precedent** | `AuthorizationServerConfig.authorizationStoreObjectMapper()` — `SecurityJackson2Modules` + `OAuth2AuthorizationServerJackson2Module` + allowlist mixins for `IgrpOidcUser`, `OidcContextAuthenticationToken`, `UserProfile` and the `Collections.empty*` singletons |
| Session cookie hardening | `SameSite=Lax`, `Secure`, `HttpOnly` already set |
| Logout cookie clearing | `SessionLogoutHandler.CLEARABLE_COOKIES` already includes `SESSION`, Spring Session's default cookie name, alongside `JSESSIONID` |

Spring Boot 3.5.14, so `spring-session-data-redis` is version-managed by the parent.

**The precedent is the headline.** The hard part of this work — making these exact principal types survive a round trip through an external store — is already solved, in production, for the authorization store. This scope is largely about pointing the same mapper at a second store.

---

## 2. What is in the session

Verified by reading the security configuration, not assumed:

1. **`SPRING_SECURITY_CONTEXT`** — the AS chain sets `HttpSessionSecurityContextRepository` explicitly (`AuthorizationServerConfig:180`). For federated login the authentication is an OAuth2 token whose principal is `IgrpOidcUser`, which holds a `UserProfile`.
2. **The saved `OAuth2AuthorizationRequest`** — from the default repository. Carries `state`, `additionalParameters` and the **PKCE `code_verifier`** in its attributes.
3. **`SavedRequest`** — `HttpSessionRequestCache` is set explicitly (`AuthorizationServerConfig:181`).
4. **Spring Authorization Server's own authorize-endpoint state.**

**Not** in the session: the CSRF token. CSRF is disabled on both chains (`AuthorizationServerConfig:177`, `OAuth2SecurityConfiguration:88`), so there is nothing to serialize there.

---

## 3. Serializer decision

This is the whole estimate. Two options.

### 3a. JDK serialization — Spring Session's default

Cheapest wiring, but **it does not work as the code stands**. `IgrpOidcUser extends DefaultOidcUser` (serializable) but holds `private final UserProfile userProfile`, and `UserProfile` is a `record` carrying Jackson annotations with **no `Serializable`**. Writing the security context would fail with `NotSerializableException`.

Making it work means adding `Serializable` to `UserProfile` and auditing its components, then accepting:

- the stored format is coupled to class shape — adding or renaming a field invalidates every live session unless `serialVersionUID` is managed deliberately;
- sessions are opaque blobs in Redis, so an operator cannot inspect one;
- a second, independent serialization contract to maintain beside the JSON one that already exists.

### 3b. JSON via the existing mapper — **recommended**

Configure Spring Session's `RedisSerializer` with an `ObjectMapper` built the same way as `authorizationStoreObjectMapper`. The three mixins that matter (`IgrpOidcUser`, `OidcContextAuthenticationToken`, `UserProfile`) are **already written and already proven** against these classes.

- No new `Serializable` surface, no `serialVersionUID` discipline.
- Sessions are readable in Redis, which matters when debugging a login that fails on one pod.
- One serialization contract for security objects, not two.
- Cost: the mapper must additionally cover `OAuth2AuthorizationRequest` and `DefaultSavedRequest`, which the authorization store does not exercise. Spring Security ships Jackson modules for both; `SecurityJackson2Modules.getModules()` is already in the builder, so this is expected to be configuration rather than new mixins — **but it is the one unproven step and should be spiked first** (§6).

Recommendation: **3b**, with the §6 spike before committing to an estimate.

---

## 4. Work breakdown

1. **Spike** the serializer against a real login (§6). Half a day. Decides everything below.
2. Add `spring-session-data-redis`; set `spring.session.store-type=redis`, a key namespace, and `spring.session.timeout` aligned to R3.1's `server.servlet.session.timeout`.
3. Extract the mapper currently built inside `AuthorizationServerConfig.authorizationStoreObjectMapper()` into a shared `@Bean` so both the authorization store and the session serializer use one definition. Avoids the two drifting apart.
4. Register the Spring Session `RedisSerializer` with that mapper.
5. Verify `changeSessionId` session-fixation protection still behaves (Spring Session supports it; confirm the id actually rotates on login).
6. Confirm `SessionLogoutHandler` still clears the cookie — `SESSION` is already in `CLEARABLE_COOKIES`, so this is a check, not a change.
7. Tests (§5) and a short runbook note (§7).

---

## 5. Validation

- **V1** Sign in through the federated flow, then serve `/login/oauth2/code/external-idp` from a **different** pod. The login completes. This is the reported failure, and it is the acceptance test.
- **V2** Restart the pod mid-login, between `/oauth2/authorize` and the callback. The login completes.
- **V3** Two apps on one host: the second signs in via `prompt=none` with requests spread across pods, without a credential prompt.
- **V4** The session survives a round trip intact: `SecurityContext` authentication, principal type `IgrpOidcUser`, and a non-null `UserProfile` after deserialization.
- **V5** Session fixation — the session id changes on login.
- **V6** Logout clears the Redis entry and the `SESSION` cookie; a replay of the old cookie does not resurrect the session.
- **V7** Redis unavailable at login: the failure is a clean error, not a 500 stack trace. Decide and assert the intended behaviour.

---

## 6. The spike — **run 2026-10-23, answered**

> Does an `ObjectMapper` built like `authorizationStoreObjectMapper` round-trip a **real** session — `SecurityContextImpl` holding the federated authentication, plus a saved `OAuth2AuthorizationRequest` with its PKCE verifier, plus a `DefaultSavedRequest`?

**Yes, with six extra allowlist entries.** `SessionSerializationSpikeTest` (3 tests, passing) is the evidence and stays as a regression guard.

The risk was not where this document put it:

- **The two payloads flagged as unproven were fine.** `OAuth2AuthorizationRequest` round-trips with `state`, scopes, `nonce` and the **PKCE `code_verifier`** intact, and `DefaultSavedRequest` round-trips, both with **no new mixins** — Spring Security's bundled modules already cover them. The stated unknown has closed.
- **The failure was in the control**, the payload assumed proven. Deserializing the `SecurityContext` fails on `UserProfile.amr`:

  ```
  java.util.ImmutableCollections$List12 is not in the allowlist
    ... IgrpOidcUser["userProfile"] -> UserProfile["amr"]
  ```

  The allowlist admits the `Collections.empty*` sentinels but **not the `List.of()` / `Set.of()` / `Map.of()` family**. These are different classes: `List.of()` is `ImmutableCollections$ListN`, not `Collections$EmptyList`.

**Fix:** six `addMixIn` calls for `List.of()`, `List.of(a)`, `Set.of()`, `Set.of(a)`, `Map.of()`, `Map.of(a,b)` — covering the `ListN`/`List12`/`SetN`/`Set12`/`MapN`/`Map1` implementations. Add them when extracting the shared mapper (step 3).

**This is also a latent hazard in the existing mapper, not only a session concern.** `authorizationStoreObjectMapper` has the same gap. Production escapes it today by luck of construction: the OIDC login path builds `amr` from `oidcUser.getClaimAsStringList("amr")`, which yields an `ArrayList` and *is* allowlisted. But `IgrpJwtAuthenticationConverter:78` constructs a `UserProfile` with `java.util.List.of()` → `ImmutableCollections$ListN`, which is not. Whether that principal ever reaches the authorization store is path-dependent; the same six lines remove the question either way, and should be applied to the existing mapper regardless of whether this feature proceeds.

**Estimate consequence:** steps 2–4 are configuration plus those six lines. The work lands at the short end of the range, and the serialization risk that justified the spike is retired.

---

## 7. Rollout and operational notes

- **Every signed-in user is logged out once at cutover.** Existing sessions live in pod memory and are not migrated. Deploy it when that is acceptable, and say so in the release note.
- Session TTL in Redis must match `server.servlet.session.timeout` (R3.1), which is itself currently Spring's unconfigured 30-minute default and needs a deliberate value.
- Redis becomes a hard dependency of **login**, not only of caching. It already is for the permission cache, but the blast radius grows: a Redis outage stops new sign-ins. Worth stating explicitly to whoever owns the SLO.
- Key namespace must be distinct from the permission and session caches already in that Redis.
- Sticky sessions at the ingress are the cheaper alternative and are **not** equivalent: they do not survive a pod restart, which is the case already observed in production.

---

## 8. Out of scope

- `t_user_session` and anything under `igrp.session.*`.
- The logout cascade itself (`plans/sso-session-logout-cascade/`).
- A custom cookie-based `AuthorizationRequestRepository`. It would fix the authorize round-trip without Redis, but leaves the `SecurityContext` unshared, so `prompt=none` would still depend on hitting the right pod. Worth revisiting only if Redis is ruled out.
- Redis high availability.
