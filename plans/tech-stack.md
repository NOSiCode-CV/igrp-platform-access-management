# Tech Stack

Everything below is what actually ships in `version/0.2.0-beta` today (verified 2026-07-14). Version numbers are pinned in [`pom.xml`](../pom.xml) unless noted.

## Runtime

| Layer | Choice | Version | Rationale |
|---|---|---|---|
| Language | Java | **26.0.1** (2026-04-21 GA) | Latest LTS-adjacent; migrated from 25 in 2026-07. |
| Build | Maven Wrapper | 3.9.10 | Reproducible builds, no host Maven required. |
| Runtime framework | Spring Boot | 3.5.6 | Reactive-optional, actuator, OAuth2 server + resource server support. |
| Persistence | Hibernate ORM | 6.6.x (from Spring Boot BOM) | JPA 3.1. |
| Database | PostgreSQL | 15+ | Advisory locks for audit chain serialization; JSONB not currently used but reserved. |
| Migrations | Flyway | (Spring Boot managed) | Versioned SQL, `V<n>_<m>__desc.sql` layout. Never edit an applied migration. |
| Cache | Caffeine | (Spring Boot managed) | Login-event dedup cache in the audit listener; permission cache. |

## Security

| Concern | Implementation |
|---|---|
| OAuth2 Authorization Server | Spring Authorization Server (managed by Spring Boot). Endpoints under `/oauth2/*`. |
| OAuth2 Resource Server | `spring-boot-starter-oauth2-resource-server` protects `/api/*`. |
| Authorization decisions | Custom `@igrpAuthorization.checkPermission(...)` SpEL bean, backed by `PermissionCacheService`. Super-admin bypass for `DEPT_IGRP.superadmin`. |
| Audit trail | Tamper-evident append-only `t_security_audit_log` with per-row hash chain (`previous_hash`, `current_hash`, `sequence_number`); Postgres advisory lock serializes writes cluster-wide. Chain secret from `AUDIT_CHAIN_SECRET`. |
| Session management | `t_session` with revocation; `SessionAuditLogger` fan-in for all state transitions. |

## Testing

| Layer | Tool |
|---|---|
| Unit | JUnit 5 (Jupiter) + Mockito 5.x with `@ExtendWith(MockitoExtension.class)`. Lenient strictness via `@MockitoSettings(strictness = Strictness.LENIENT)` where the SUT has conditional stubbing. |
| Slice / integration | Spring Boot `@WebMvcTest`, `@DataJpaTest`, `@SpringBootTest` with Testcontainers (Postgres) for DB-touching tests. |
| Client SDK | JUnit 5 + Mockito. See `modules/client/src/test/`. |

## Build & release

| Aspect | Detail |
|---|---|
| Registry | Nexus at `https://sonatype.nosi.cv/` — `igrp-framework-releases` for release artifacts, `igrp-framework` for snapshots. |
| Java SDK groupIds | `cv.igrp.framework.auth:core`, `cv.igrp.framework.auth:core-spring-boot`, `cv.igrp.platform.access:client`. |
| TS SDK | Published via `pnpm publish` to Nexus npm-hosted repo at `https://sonatype.nosi.cv/repository/igrp/`. |
| Container | Two Dockerfiles: `Dockerfile` (JRE), `Dockerfile_native` (GraalVM native image, stubbed). No production native build yet. |

## Annotation processors

Declared via `maven-compiler-plugin`'s `annotationProcessorPaths` (see [`pom.xml:343-371`](../pom.xml)):

- `org.projectlombok:lombok` — reduce boilerplate on DTOs / entities.
- `cv.igrp.framework:core:0.1.0-beta.1` — iGRP framework core processor.
- `cv.igrp.framework:stereotype:0.1.0-beta-20251021.121417-2` — `@IgrpController`, `@IgrpService`, etc.
- `cv.igrp.framework.auth:core:0.1.0-beta.1` — authorities mapping for `@PreAuthorize`.

## Client SDKs

| SDK | Location | Version | Publish |
|---|---|---|---|
| Java | `access-management/backend/modules/client` (own repo: `igrp-framework-auth-backend-monorepo` branch `main`) | 0.2.0-beta.10 | `mvn deploy` to Nexus. |
| TypeScript | `access-management/frontend/packages` submodule, `main` branch | 0.2.0-beta.12 | `pnpm publish` to Nexus. |

## Coming with this feature (`unified-audit-and-reports`)

Additions that will land in `pom.xml`:

| Library | Version | Purpose | License |
|---|---|---|---|
| `org.apache.poi:poi-ooxml` | 5.3.0 | Excel export (SXSSF streaming to keep memory flat for large reports). | Apache 2.0 |
| `com.github.librepdf:openpdf-core-legacy` | 2.0.3 | PDF export. LGPL-safe fork of iText 4. | LGPL 2.1 |

No other stack changes. No new database, no new runtime dependency, no framework version bump.

## Things explicitly not on the stack

- **iText 5/7.** AGPL. Not compatible with the project's distribution model.
- **Flying Saucer PDF.** Depreciated, LGPL, HTML-to-PDF only.
- **Zanzibar / OpenFGA.** Considered for authorization; deferred. `permify-spring-boot` module exists for teams that need tuple-based authz.
- **Kafka / RabbitMQ.** Audit fan-out is in-process via Spring `ApplicationEventPublisher`. No message broker.
- **Reactive stack (WebFlux).** Servlet-based Spring MVC everywhere.
