# Scope — Shared servlet session across replicas

**Why:** AM may scale past one replica on demand (Kubernetes). The servlet session is in-JVM today, so anything stored in it is lost when a request lands on a different pod, or on the same pod after a restart.
**Status:** **implemented 2026-10-23.** Pending only the cutover window (§8).
**Target branch:** `development`.
**Required by:** `plans/sso-session-logout-cascade/requirements.md` R3.2 — silent sign-in (`prompt=none`) depends on this. The logout cascade itself does not (R1.6).
**Standalone value:** this fixes a live production failure regardless of whether the cascade is ever built.
**Store:** **Postgres, via `spring-session-jdbc`.** Revised 2026-10-23 — the first draft of this document assumed Redis. The reasoning for the change is in §1.1.

---

## 0. The failure it fixes

Login fails with **`authorization_request_not_found`** — rendered to users as "Login could not be completed". Reported from `api-gateway-test`.

Mechanism: no custom `AuthorizationRequestRepository` is registered, so Spring's default `HttpSessionOAuth2AuthorizationRequestRepository` stores the pending authorization request in the servlet session. The browser starts at `/oauth2/authorize` on one pod; the IdP redirects back to `/login/oauth2/code/external-idp` on another pod, or the same pod after a restart; the `state` matches no saved request.

A single replica does not make this safe — a rolling deploy or an OOM-kill reproduces it for anyone mid-login. Scaling makes it routine rather than occasional.

`prompt=none` multiplies the exposure: every additional app on the host is another authorize round-trip through AM.

**Not related** to `t_user_session` / `igrp.session.*`. That is AM's own session model, already in Postgres with a Redis cache, and is unaffected by this work. The two are easy to confuse and are not the same thing.

---

## 1. Store choice

### 1.1 Postgres (`spring-session-jdbc`) — **recommended**

Two facts decide it.

**Postgres is already a hard dependency of login; Redis is not.** A federated sign-in cannot complete without Postgres: `IgrpOidcUserService` reads and writes `t_user` on every sign-in, and `SessionIssuanceService` writes `t_user_session` at token issuance. Putting the servlet session there adds **no new failure mode** — if Postgres is down, login was already failing.

Choosing Redis would, by contrast, make a Redis outage stop new sign-ins, which it does not do today: Redis currently backs only caches, and `CacheConfig`'s `FallbackCacheManager` / `SafeCache` deliberately swallows `RedisConnectionFailureException` so the permission path degrades to its JDBC queries. That graceful-degradation property **cannot** be extended to sessions. A cache miss is safe only when the value is derivable; a session is the authoritative record of an authentication, so swallowing the error would silently sign everyone out — reproducing the very failure in §0 rather than surviving it.

**The write volume is negligible, because the API never creates a session.** `OAuth2SecurityConfiguration:89` sets `SessionCreationPolicy.STATELESS` on the resource-server chain, so JWT API traffic — the overwhelming majority of requests — creates and touches nothing. Only the browser authorization-server chain uses a session (`AuthorizationServerConfig:181`, `IF_REQUIRED`). That is a handful of rows and `last_accessed_time` updates **per login**, not per request. The usual objection to a JDBC session store does not apply to this service's traffic shape.

Secondary benefits: sessions are inspectable with SQL when debugging a login that failed on one pod, and the store inherits Postgres's existing backup and HA arrangements instead of needing its own.

### 1.2 Redis (`spring-session-data-redis`) — documented alternative

Viable, and the dependency (`spring-boot-starter-data-redis`) is already present with a connection required at startup. Rejected for now only because it widens the blast radius of a Redis outage from "caches degrade" to "nobody can sign in", for no benefit at this volume.

Revisit if session volume stops being login-shaped — for example if the resource-server chain stops being stateless, or AM starts serving a stateful UI of its own.

### 1.3 A composite Redis-cache-over-Postgres repository — rejected

Tempting by analogy with `PermissionCacheService`, and explicitly **not** recommended. It means writing and maintaining write-through and invalidation logic for authentication state, to optimise a load that is already trivial (§1.1). `spring-session-jdbc` is off-the-shelf and correct; a composite is bespoke code that would have to be made correct. Parked, not planned.

---

## 2. What is already in place

| | |
|---|---|
| Postgres | required at startup (`DATABASE_URL`), and already required for login to succeed |
| DDL migration path | `DatabaseMigrationRunner` (`shared/config/`) runs idempotent DDL at startup over JDBC — this repository's established home for custom schema changes |
| **Security-object JSON precedent** | `AuthorizationServerConfig.authorizationStoreObjectMapper()` — `SecurityJackson2Modules` + `OAuth2AuthorizationServerJackson2Module` + allowlist mixins for `IgrpOidcUser`, `OidcContextAuthenticationToken`, `UserProfile`, the `Collections.empty*` singletons and the `List.of()` / `Set.of()` / `Map.of()` family |
| Session cookie hardening | `SameSite=Lax`, `Secure`, `HttpOnly` already set |
| Logout cookie clearing | `SessionLogoutHandler.CLEARABLE_COOKIES` already includes `SESSION`, Spring Session's default cookie name, alongside `JSESSIONID` |
| Stateless API chain | `OAuth2SecurityConfiguration:89` — `SessionCreationPolicy.STATELESS`, so the store sees login traffic only |

Spring Boot 3.5.14, so `spring-session-jdbc` is version-managed by the parent.

**The precedent is the headline.** The hard part of this work — making these exact principal types survive a round trip through an external store — is already solved, in production, for the authorization store, and the spike (§6) has now confirmed it covers everything the session holds. This scope is largely about pointing the same mapper at a second store.

---

## 3. What is in the session

Verified by reading the security configuration, not assumed:

1. **`SPRING_SECURITY_CONTEXT`** — the AS chain sets `HttpSessionSecurityContextRepository` explicitly (`AuthorizationServerConfig:180`). For federated login the authentication is an OAuth2 token whose principal is `IgrpOidcUser`, which holds a `UserProfile`.
2. **The saved `OAuth2AuthorizationRequest`** — from the default repository. Carries `state`, `additionalParameters` and the **PKCE `code_verifier`** in its attributes.
3. **`SavedRequest`** — `HttpSessionRequestCache` is set explicitly (`AuthorizationServerConfig:181`).
4. **Spring Authorization Server's own authorize-endpoint state.**

**Not** in the session: the CSRF token. CSRF is disabled on both chains (`AuthorizationServerConfig:177`, `OAuth2SecurityConfiguration:88`), so there is nothing to serialize there.

---

## 4. Serializer decision

Spring Session JDBC stores each attribute as `BYTEA` and converts it with a `ConversionService`. Its default is JDK serialization. Two options — unchanged in substance by the move from Redis to Postgres; only the integration point differs (a `springSessionConversionService` bean instead of a `RedisSerializer`).

### 4a. JDK serialization — the default

Cheapest wiring, but **it does not work as the code stands**. `IgrpOidcUser extends DefaultOidcUser` (serializable) but holds `private final UserProfile userProfile`, and `UserProfile` is a `record` carrying Jackson annotations with **no `Serializable`**. Writing the security context would fail with `NotSerializableException`.

Making it work means adding `Serializable` to `UserProfile` and auditing its components, then accepting:

- the stored format is coupled to class shape — adding or renaming a field invalidates every live session unless `serialVersionUID` is managed deliberately;
- sessions are opaque blobs, so an operator cannot inspect one;
- a second, independent serialization contract to maintain beside the JSON one that already exists.

### 4b. JSON via the existing mapper — **recommended**

Register a `springSessionConversionService` whose `Object → byte[]` and `byte[] → Object` converters use an `ObjectMapper` built the same way as `authorizationStoreObjectMapper`.

- No new `Serializable` surface, no `serialVersionUID` discipline.
- Sessions are readable with SQL, which matters when debugging a login that fails on one pod.
- One serialization contract for security objects, not two.
- **Proven.** §6 settled the one unknown: the mapper round-trips all three payloads.

Recommendation: **4b**.

---

## 5. Work breakdown — **done 2026-10-23**

1. ~~Spike the serializer against a real login.~~ Done (§6).
2. ~~Add `spring-session-jdbc`;~~ `spring.session.store-type=jdbc`, `spring.session.jdbc.initialize-schema=never`. Version is managed by the Boot parent, so nothing to pin.
3. ~~Create the Spring Session tables in `DatabaseMigrationRunner`~~ — transcribed from `schema-postgresql.sql`, written `IF NOT EXISTS`. Unlike the neighbouring migrations, failures are **not** swallowed: missing tables break every login, so failing loudly beats serving traffic that cannot authenticate.
4. ~~Extract the mapper into one shared definition~~ — `SecurityStoreObjectMapperFactory`, used by both the authorization store and the session converters.
5. ~~Register the `springSessionConversionService`~~ — `SessionStoreConfig`.
6. ~~Set the timeout~~ — `server.servlet.session.timeout=${IGRP_SSO_SESSION_TIMEOUT_SECONDS:28800}s`, documented in `.env.example` next to the `IGRP_SESSION_*` family it is easy to confuse with.
7. ~~Confirm the cleanup job.~~ Left at Boot's default cron.
8. Session fixation — `changeSessionId` still applies; **confirm the id rotates on login during V1–V3**, which is manual verification rather than code.
9. ~~Confirm `SessionLogoutHandler` clears the cookie~~ — `SESSION` was already in `CLEARABLE_COOKIES`.
10. ~~Tests~~ — `SessionStoreConversionTest` (4) and `ResourceServerChainStatelessTest` (1). Suite: 978, 0 failures.

### Two things the implementation found

**The shared mapper must not be a bean.** Step 4 originally said "shared `@Bean`". Publishing an `ObjectMapper` bean satisfies the `@ConditionalOnMissingBean(ObjectMapper.class)` guard in Boot's `JacksonAutoConfiguration`, which would silently replace the mapper used for **HTTP message conversion** with this one — class-name default typing and an allowlist, correct for a trusted store and wrong for request bodies. It is a static factory instead: one definition, none of the hazard.

**Postgres needs the upsert customizer, and Boot does not register it.** Spring Session's default attribute write is a plain `INSERT`. Boot's `JdbcSessionConfiguration` contributes a customizer that sets only the timeout, table name and cleanup cron — it does **not** apply `PostgreSqlJdbcIndexedSessionRepositoryCustomizer`, which swaps the insert for `ON CONFLICT ... DO UPDATE`. Without it, two concurrent requests on one session that both see an attribute as new race on the `(SESSION_PRIMARY_ID, ATTRIBUTE_NAME)` primary key and the loser fails. The authorize flow does issue concurrent requests against one session, and this is the third duplicate-key defect in this area, so it is registered explicitly in `SessionStoreConfig`.

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

**Fix: applied 2026-10-23, independently of this feature.** Six `addMixIn` calls — for `List.of()`, `List.of(a)`, `Set.of()`, `Set.of(a)`, `Map.of()` and `Map.of(a,b)`, covering the `ListN` / `List12` / `SetN` / `Set12` / `MapN` / `Map1` implementations — are now in `authorizationStoreObjectMapper`.

They were applied ahead of this work because **the gap was a latent hazard in the existing mapper, not only a session concern.** Production escaped it by luck of construction: the OIDC login path builds `amr` from `oidcUser.getClaimAsStringList("amr")`, which yields an allowlisted `ArrayList`, but `IgrpJwtAuthenticationConverter:78` constructs a `UserProfile` with `java.util.List.of()` → `ImmutableCollections$ListN`, which was not. Whether that principal ever reached the authorization store was path-dependent; the six lines remove the question either way.

**Estimate consequence:** steps 2–5 are configuration plus one small bean. The serialization risk that justified the spike is retired, and the store choice (§1) removed the remaining architectural question.

---

## 7. Validation

- **V1** Sign in through the federated flow, then serve `/login/oauth2/code/external-idp` from a **different** pod. The login completes. This is the reported failure, and it is the acceptance test.
- **V2** Restart the pod mid-login, between `/oauth2/authorize` and the callback. The login completes.
- **V3** Two apps on one host: the second signs in via `prompt=none` with requests spread across pods, without a credential prompt.
- **V4** The session survives a round trip intact: `SecurityContext` authentication, principal type `IgrpOidcUser`, and a non-null `UserProfile` after deserialization.
- **V5** Session fixation — the session id changes on login.
- **V6** Logout clears the `SPRING_SESSION` row and the `SESSION` cookie; a replay of the old cookie does not resurrect the session.
- **V7** An expired session is deleted by the cleanup job, and a request carrying its cookie starts a fresh login rather than erroring.
- **V8** API traffic creates **no** `SPRING_SESSION` rows. This is the assumption §1.1 rests on, so assert it rather than trust it: a future chain change that quietly drops `STATELESS` would turn every API call into a session write.

---

## 8. Rollout and operational notes

- **Every signed-in user is logged out once at cutover.** Existing sessions live in pod memory and are not migrated. Deploy it when that is acceptable, and say so in the release note. This is the one remaining acceptance decision.
- `server.servlet.session.timeout` (R3.1) is still Spring's unconfigured 30-minute default and needs a deliberate value. **Recommendation: 28800 s (8 h), via an env override**, matching `igrp.session.absoluteTimeoutSeconds`. It is the window in which opening a second app signs the user in silently; the app sessions it creates already run to an 8-hour ceiling, so aligning the two gives one lifetime story — the SSO session and the app sessions end together. It should never be set *above* that ceiling: an SSO session outliving every app session it can create is a window with nothing visible behind it. Today's 30 minutes is the worst of both, because a user who signs into one app at 09:00 and opens another at 10:00 gets an unexplained prompt.
- **Postgres does not become a new dependency of login.** It already is one (§1.1). There is no SLO change to negotiate, which is the main practical gain over Redis.
- Two expiry mechanisms now coexist and must not be confused: `spring.session.jdbc.cleanup-cron` prunes `SPRING_SESSION`, while `SessionCleanupScheduler` expires `t_user_session`. Different tables, different meanings.
- Sticky sessions at the ingress are the cheaper alternative and are **not** equivalent: they do not survive a pod restart, which is the case already observed in production.

---

## 9. Out of scope

- `t_user_session` and anything under `igrp.session.*`.
- The logout cascade itself (`plans/sso-session-logout-cascade/`).
- Redis as the session store (§1.2) and a Redis-over-Postgres composite (§1.3). Both are recorded with their reasoning rather than dropped.
- A custom cookie-based `AuthorizationRequestRepository`. It would fix the authorize round-trip without a shared store, but leaves the `SecurityContext` unshared, so `prompt=none` would still depend on hitting the right pod.
- Postgres high availability.
