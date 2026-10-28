# Impact Analysis — Migrating to Spring Boot 4.1.x

**Date:** 2026-10-28
**Scope:** `igrp-platform-access-management` and the iGRP Spring libraries it depends on (`core-spring-boot` and siblings)
**Question:** what would this migration cost, and is there any impact at all?
**Status:** analysis only. No code changed. Nothing here is a recommendation to start.

---

## Verdict

**High impact, and currently blocked on work outside this repository.** This is not a
version bump; it is a coordinated upgrade across at least three repositories, and one
external dependency is not yet GA.

Three findings drive the conclusion:

1. **The iGRP framework libraries are the gate.** `core-spring-boot` hard-pins Spring Boot
   **3.5.6** on each dependency and is published as a compiled JAR. Until it is rebuilt
   against Boot 4, upgrading this service means running bytecode linked against Spring
   Security 6 on a Spring Security 7 classpath. Nothing in this repository can work around
   that.
2. **Spring Cloud is not ready.** The train that targets Boot 4.1 is 2025.1.x, and users
   report its compatibility verifier rejecting Boot 4.1.x. Spring Cloud 2026.0.0 was still
   **RC1** as of 2026-10-23 and is built on Boot 4.2.0-RC1 — it skips 4.1 rather than
   targeting it.
3. **Jackson 2 → 3 lands squarely on the code this service is most sensitive about.**
   Every custom serializer here exists to persist authentication state. That is the one
   area where a silent format change is not a bug report, it is users unable to log in.

**Order-of-magnitude estimate: 3–6 weeks of engineering across the repositories**, most of
it verification rather than typing. There is no plausible reading of this as "no impact".

**Recommendation: do not start now.** Wait for Spring Cloud 2026.0.0 GA and target **Boot
4.2**, not 4.1. Spend the interval rebuilding the framework libraries, which is useful
work regardless. Reasoning in [§7](#7-recommendation).

---

## 1. Current state

| | Current | Boot 4.1 target |
|---|---|---|
| Spring Boot | 3.5.14 | 4.1.2 (latest at time of writing) |
| Spring Framework | 6.2.x | 7.0.10+ |
| Spring Security | 6.5.x | 7.1.x |
| Spring Authorization Server | 1.5.x (standalone) | **folded into Spring Security 7** |
| Spring Cloud | 2025.0.0 | 2025.1.x (contested) / 2026.0.0 (RC1) |
| Jackson | 2.21.2 (`com.fasterxml.jackson`) | 3.x (`tools.jackson`) |
| springdoc-openapi | 2.8.17 | 3.0.x |
| Java | 26 | 17+ required, ≤27 supported — **no change needed** |
| Servlet | 6.0 (Tomcat 10.1.55, pinned) | 6.1 baseline (Tomcat 11) |

Java is the one axis already satisfied. Being on 26 is an asset here: the hard part of
most Boot 4 migrations is the JDK baseline, and that is already paid for.

---

## 2. Blocker 1 — the iGRP framework libraries

This is the finding that determines the schedule, and it is not visible from this
repository's `pom.xml`.

### What the dependency looks like

`access-management/backend/modules` (the framework-auth monorepo) has **no Spring Boot
parent and no imported BOM**. Each module pins Spring per-dependency. In
`core-spring-boot/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    <version>3.5.6</version>          <!-- compile scope, not provided -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <scope>provided</scope>
    <version>3.5.6</version>
</dependency>
```

### Why it blocks

Maven's dependency management in this service would force the *versions* up to 4.1, so the
build would likely still resolve. That is the trap: **it resolves and compiles, then fails
at runtime**, because the published `core-spring-boot` bytecode is linked against Spring
Security 6 method signatures. Removed or relocated APIs surface as `NoSuchMethodError` or
`NoClassDefFoundError` on the first call, not at startup.

The exposure is concentrated in the worst possible place. Of 12 source files in
`core-spring-boot`, the Spring APIs used include:

| API package | Risk |
|---|---|
| `org.springframework.security.access.expression.method` | **High** — Spring Security 7 relocated the access/authorization APIs |
| `org.springframework.security.config.annotation.method.configuration` | **High** — same area |
| `org.springframework.security.oauth2.server.resource.authentication` | Medium |
| `org.springframework.security.core.*` | Low — stable surface |
| `org.springframework.boot.autoconfigure.condition` | Low |

The two high-risk entries are the method-security expression handler, which is exactly
what this service's 134 `@PreAuthorize` annotations resolve through.

### What has to happen

The framework-auth monorepo needs its own migration, and it should acquire a Boot BOM
rather than per-dependency pins while it is being touched. Scope guesses, to be confirmed
by whoever owns it: `core`, `core-spring-boot`, `keycloak-spring-boot`,
`permify-spring-boot`, `wso2-spring-boot`. The same question applies to
`cv.igrp.framework:core`, `cv.igrp.framework:stereotype`, the filemanager modules and
`cv.igrp.platform.notification:client` — each is a compiled artifact with its own Spring
linkage, and each needs checking.

**This work is a prerequisite and it is not ours.** Any plan that does not start here is
not a plan.

---

## 3. Blocker 2 — Spring Cloud

This service uses the Spring Cloud BOM (2025.0.0) for Eureka discovery
(`EUREKA_CLIENT_ENABLED`, optional but wired).

- **2026.0.0 is RC1**, announced 2026-10-23, and is based on **Boot 4.2.0-RC1**. It targets
  4.2, not 4.1.
- **2025.1.x** is the train claiming Boot 4.1 support — 2025.1.2 is described as compatible
  with Boot 4.0.7 and introducing Boot 4.1.0 compatibility.
- Multiple issue reports describe the compatibility verifier refusing to start on Boot
  4.1.0 and 4.1.1, with `spring.cloud.compatibility-verifier.enabled=false` as the
  circulating workaround.

Disabling a compatibility verifier to force an unsupported combination is not a
configuration choice, it is suppressing a warning that exists for a reason. That workaround
should not go into a production authorization server.

This is the single strongest argument for targeting **4.2** instead of 4.1: it converts a
contested combination into a supported one at no extra cost.

---

## 4. The large, well-defined work

Once unblocked, this is real but tractable — mostly mechanical, with one area that needs
genuine care.

### 4.1 Jackson 2 → 3 — the main event

Boot 4 moves to Jackson 3. The package root changes from `com.fasterxml.jackson` to
`tools.jackson`; only `com.fasterxml.jackson.annotation` keeps its old name.

Measured exposure in this service: **40 `com.fasterxml.jackson` references across 20
files**, of which 15 are annotations (which do *not* move) and 21 are `databind` (which
do). So the raw rename is small.

The problem is not the count, it is *which* files:

| Class | Why it matters |
|---|---|
| `SecurityStoreObjectMapperFactory` | Builds the mapper for **both** the OAuth2 authorization store and the servlet session store |
| `AuthorizationServerConfig#authorizationService` | Persists `OAuth2Authorization` rows |
| `SessionStoreConfig#springSessionConversionService` | Serializes `SPRING_SESSION_ATTRIBUTES` |
| `IgrpOidcUser`, `UserProfile` | The principal, with allowlist mixins |

All of it is built on `SecurityJackson2Modules` (17 references). In Spring Security 7 that
class is **deprecated** in favour of `SecurityJacksonModules`, in a new package
`org.springframework.security.jackson`, configured through a Jackson 3
`JsonMapper.Builder` rather than an `ObjectMapper`. `OAuth2AuthorizationServerJackson2Module`
and `CoreJackson2Module` have equivalent successors.

**The good news, and it is significant.** Spring Security's Jackson 3 support is documented
as using a wire format compatible with the Jackson 2 one, so **previously serialized data
should still deserialize**. If that holds, there is no data migration: existing
`OAuth2Authorization` rows and `SPRING_SESSION_ATTRIBUTES` survive the upgrade, and nobody
is logged out by the format change.

**It must be proven, not assumed.** Fortunately the harness already exists — three tests
written for the shared-session work do exactly this job and would become the migration's
acceptance criteria:

- `SessionStoreConversionTest` — round-trips the real conversion-service bean
- `IgrpOidcUserSerializationTest` — round-trips the principal and its SSO login id
- `SessionSerializationSpikeTest` — the authorization-request and saved-request payloads

The specific test to add: write a payload with the Jackson 2 stack, read it with the
Jackson 3 stack. That is the only assertion that answers the question for *our* types
rather than in general.

**One decision inverts, and happens to land correctly.** `SecurityStoreObjectMapperFactory`
is deliberately a static factory, not a bean, because Boot 3's `JacksonAutoConfiguration`
backs off when it finds an `ObjectMapper` bean — publishing one would have silently
replaced the HTTP message-conversion mapper. In Boot 4 the rule is reversed: defining an
`ObjectMapper` bean *no longer* overrides the auto-configured mapper, because a `JsonMapper`
bean is what counts. The design stays right; **the comment explaining why becomes wrong**
and must be rewritten, or the next person will "fix" it based on a stale premise.

Also in scope: `Jackson2ObjectMapperBuilderCustomizer` → `JsonMapperBuilderCustomizer`,
`@JsonComponent` → `@JacksonComponent`, `@JsonMixin` → `@JacksonMixin`,
`spring.jackson.read.*`/`write.*` → `spring.jackson.json.read`/`write`.

A compatibility bridge exists — `spring.jackson.use-jackson2-defaults` and a deprecated
`spring-boot-jackson2` module. **Treat the bridge as a rollback lever, not a destination.**
Shipping on a deprecated compatibility shim converts a one-time migration into permanent
debt.

> **Behavioural change worth flagging separately:** Jackson 2's `JsonProcessingException`
> extended `IOException`; Jackson 3's `JacksonException` extends `RuntimeException`. Any
> `catch (IOException)` around parsing stops catching parse failures. `SessionStoreConfig`
> wraps converter failures in `UncheckedIOException` and its catch blocks need re-reading
> against the new hierarchy — a swallowed-then-escaping serialization error there means a
> broken login.

### 4.2 Spring Security 7 and the Authorization Server

The structural change: **Spring Authorization Server no longer exists as a separate
project.** 1.5.x is the last standalone generation; the line was renumbered to 7.x and
merged into Spring Security.

Concretely for this service:

- `spring-boot-starter-oauth2-authorization-server` → **`spring-boot-starter-security-oauth2-authorization-server`**
- The `spring-authorization-server.version` property stops working; use
  `spring-security.version`
- Method-security APIs relocated — this service has `MethodSecurityConfig`, a custom
  `IgrpMethodSecurityExpressionHandler`, and 134 `@PreAuthorize` sites resolving through it.
  **The highest-risk area in this repository**, because a permission check that fails *open*
  is a security incident rather than an outage, and a unit test that mocks the handler will
  not notice.
- `authorizeRequests()` is fully removed — not used here; this service already uses
  `authorizeHttpRequests`. No work.
- **CSRF defaults reportedly change** so that API endpoints get CSRF protection by default.
  This service explicitly disables CSRF on both chains, with a long comment explaining why,
  so it is insulated — but that disable must survive the migration, and the reasoning should
  be re-validated rather than assumed still true.
- `@WithMockUser` / `@WithUserDetails` now need `spring-boot-starter-security-test`.

### 4.3 Artifact and starter renames

| Current | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `spring-boot-starter-oauth2-authorization-server` | `spring-boot-starter-security-oauth2-authorization-server` |
| `spring-boot-starter-test` | per-technology `spring-boot-starter-<tech>-test` |
| bare `flyway-core` | `spring-boot-starter-flyway` |
| `hibernate-jpamodelgen` | `hibernate-processor` |

The autoconfigure jar is split into per-technology modules; `spring-boot-starter-classic`
exists as a transitional catch-all. Using it would get a build green quickly and leave the
real module graph undone — acceptable as a mid-migration step, not as an end state.

---

## 5. The small, mechanical work

| Item | Size |
|---|---|
| `@MockBean` / `@SpyBean` → `@MockitoBean` / `@MockitoSpyBean` | **12 occurrences**, trivial |
| Tomcat pin `10.1.55` → Tomcat 11 (Servlet 6.1 baseline) | 1 line; the pin exists as a CVE override and must be re-based |
| Other explicit pins to re-evaluate against Boot 4's managed set | `netty-bom`, `httpclient` 4.5.14, `postgresql`, `log4j-api`, `protobuf-java`, `commons-io`, `bcpkix` — roughly 10 lines, each a CVE override that may now be redundant or conflicting |
| springdoc 2.8.17 → 3.0.x | 1 property; the 3.x line is the Boot 4 line |
| Spring Session property moves | Redis and Mongo properties moved under `spring.session.data.*`. **This service uses JDBC**, which the guide does not list as moved — verify, do not assume |
| Jakarta EE 11 / Servlet 6.1 API drift | No `javax.*` remains; low risk |

Flyway deserves a note: the dependency is declared bare (`flyway-core` +
`flyway-database-postgresql`) and Flyway **is active** — `spring.flyway.enabled=true` with
16+ versioned scripts under `src/main/resources/db/migration`. Boot 4 wants the starter.

---

## 6. Risk assessment

Ranked by what would hurt most, not by likelihood.

| Risk | Severity | Why |
|---|---|---|
| **Framework JARs fail at runtime, not build time** | **Critical** | Compiles clean, breaks on first call. Mitigation: rebuild them first; do not rely on version forcing. |
| **Method-security regression that fails open** | **Critical** | 134 `@PreAuthorize` sites through a custom expression handler, in the API *and* in `core-spring-boot`. A check that stops being enforced is silent. Mitigation: an integration test per permission class, asserting 403 for an unauthorised caller — not a mocked handler. |
| **Persisted authentication state becomes unreadable** | **High** | `OAuth2Authorization` rows and `SPRING_SESSION_ATTRIBUTES`. Expected to be compatible, but if wrong, every active user is logged out and mid-flight logins fail. Mitigation: the cross-version round-trip test above, before anything is deployed. |
| **Spring Cloud verifier refuses to start** | **High** | Known-reported on 4.1.x. Mitigation: target 4.2 / 2026.0.0 GA. |
| **Jackson exception-hierarchy change swallows errors** | Medium | `RuntimeException` instead of `IOException`. Mitigation: re-read every catch around serialization. |
| **CVE pins silently reverted** | Medium | ~10 explicit overrides exist for a reason that is not recorded in the pom. Mitigation: re-run `mvn dependency-check:check` and compare before/after. |
| **Shipping on the Jackson 2 compatibility shim** | Medium | Easy to reach for under deadline, deprecated on arrival. Mitigation: treat as rollback only. |
| Starter renames | Low | Build-time, loud, mechanical. |

### The testing gap that matters here

The suite is **999 tests and entirely unit-level with mocks — it does not start an
application context.** For this migration that is a serious gap, because the failure modes
above are precisely the ones unit tests cannot see: bean wiring, auto-configuration,
filter-chain assembly, method-security interception, serializer registration.

A green suite after this migration would carry almost no information. **The migration needs
integration tests that do not exist yet**, and building them should be counted as part of
its cost rather than assumed away. That is also why the estimate is weighted toward
verification.

---

## 7. Recommendation

**Do not start now. Target Boot 4.2 on Spring Cloud 2026.0.0 GA, not Boot 4.1.**

Reasons:

1. Boot 4.1 plus Spring Cloud is a contested combination today; 4.2 plus 2026.0.0 is the
   combination the ecosystem is converging on. Choosing 4.1 means paying for the
   compatibility-verifier problem and migrating again later.
2. The gating work is in the framework libraries and is independent of which Boot 4 version
   is chosen. That work can start immediately and is where the schedule actually lives.
3. Nothing is forcing the issue. Boot 3.5.x is supported, this service is current on it, and
   Java 26 already provides the runtime benefits the JDK baseline usually motivates.

### Sequence, if and when it proceeds

| Phase | Work | Owner |
|---|---|---|
| 0 | Confirm whether the framework libraries have a Boot 4 roadmap at all. **Everything else is downstream of this answer.** | framework team |
| 1 | Add integration tests to this service: context start, filter-chain assembly, one real 403-for-unauthorised test per permission class, cross-version serialization round-trip | this team, **can start now** |
| 2 | Rebuild framework libraries on Boot 4 with a proper BOM instead of per-dependency pins | framework team |
| 3 | Pom work here: parent, starter renames, pin re-evaluation, springdoc 3 | this team |
| 4 | Jackson 3 migration behind the serialization tests from phase 1 | this team |
| 5 | Spring Security 7: starter rename, method-security handler, re-validate the CSRF decision | this team |
| 6 | Deploy to a non-production environment and re-run the session and logout-cascade verification (V1–V7) | both |

Phase 1 is worth doing **whether or not the migration ever happens.** The absence of
context-level tests already cost a production incident in this service — a bean-name
collision that the 999-test suite could not see, found only on deployment. The migration
makes that gap expensive; it does not create it.

---

## 8. What this analysis could not verify

Stated so nobody treats the estimate as firmer than it is.

- **Whether the iGRP framework libraries have a Boot 4 plan.** The decisive input, and it
  is not knowable from the code. Phase 0 exists for this.
- **Whether `spring.session.jdbc.*` properties moved.** Redis and Mongo did; JDBC is not
  mentioned either way in the guide.
- **Whether Jackson 2 → 3 wire compatibility holds for our specific types.** Documented in
  general; unproven for `IgrpOidcUser`, `UserProfile` and the allowlisted collection
  sentinels.
- **The exact Spring Security 7 relocation targets** for the method-security expression
  APIs. Known to have moved; the precise replacements need the 7.x Javadoc against the
  chosen version.
- **Whether the ~10 explicit CVE pins are still required.** Each needs checking against
  Boot 4's managed versions.
- **Hibernate version and Envers behaviour under Boot 4.** All entities here are
  `@Audited`. The migration guide does not state the Hibernate version, and Envers
  behaviour under Hibernate 7 was not researched. **A gap in this analysis, not a
  judgement that it is safe.**

---

## Sources

Spring Boot and Spring Security documentation was used where available; several details
come from secondary write-ups and are marked as contested where the sources disagree.

- [Spring Boot 4.0 Migration Guide (official wiki)](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Migrating to Spring Security 7.0](https://docs.spring.io/spring-security/reference/migration/index.html)
- [`SecurityJackson2Modules` Javadoc (deprecation)](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/jackson2/SecurityJackson2Modules.html)
- [Spring Security deprecated list (7.1.1)](https://docs.spring.io/spring-security/reference/api/java/deprecated-list.html)
- [Spring Cloud 2026.0.0-RC1 announcement](https://spring.io/blog/2026/10/23/spring-cloud-2026-0-0-RC1-has-been-released/)
- [springdoc-openapi](https://github.com/springdoc/springdoc-openapi)
- [Spring Boot releases](https://github.com/spring-projects/spring-boot/releases)
- [The Ultimate Guide to Spring Security 7 (Migrating from 6)](https://stevenpg.com/posts/ultimate-guide-spring-security-7-migration/) — secondary
- [Spring Boot 4.0 breaking changes overview](https://www.herodevs.com/blog-posts/spring-boot-4-0-breaking-changes-migration-guide) — secondary
