# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (skip tests)
mvn -B -DskipTests clean package

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=CreateApplicationCommandHandlerTest

# Run with development profile (enables JPA DDL update and SQL logging)
mvn spring-boot:run -Dspring-boot.run.profiles=development

# OWASP dependency vulnerability scan
mvn dependency-check:check

# Build Docker image (JVM)
docker build -t access-management-api .
```

## Required Environment Variables

The application requires these at startup; without them it will fail:

| Variable | Purpose | Dev default |
|---|---|---|
| `AUTH_JWT_ISSUER` | OAuth2/JWT issuer URI | (none — required) |
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/postgres` |
| `REDIS_HOST` | Redis hostname | (none — required) |
| `REDIS_PORT` | Redis port | `6379` |

Optional (default to disabled/dev values): `EUREKA_CLIENT_ENABLED`, `IGRP_STORAGE_PROVIDER`, `SMTP_HOST`, `OTEL_COLLECTOR_ENDPOINT`, `ENABLE_SWAGGER`.

Use `application-development.properties` profile to enable `spring.jpa.hibernate.ddl-auto=update` and SQL logging.

## Architecture Overview

This is a **Spring Boot 3.5 IAM (Identity & Access Management)** service structured with **domain-driven design (DDD)** and **CQRS-lite** patterns.

### Domain Modules

Each top-level package under `cv.igrp.platform.access_management` is a bounded context:

- **`app`** — Application registration and resource/menu linking
- **`authorization`** — Real-time permission checking with Redis cache
- **`department`** — Department-scoped role and permission management
- **`files`** — File/document management (MinIO/S3 backed)
- **`global_configuration`** — System-wide settings CRUD
- **`m2m`** — Machine-to-machine sync: external systems push applications, permissions, menus via idempotent SHA-256 structural hashing
- **`notification`** — Email/notification delivery adapters
- **`role`** — Role hierarchy and assignment management
- **`security_audit`** — Immutable security event log (logins, access denials, role changes)
- **`session`** — Session lifecycle (one active session per user, Redis-backed, IP/device tracking)
- **`users`** — User lifecycle including temporary role assignment with automatic expiration
- **`shared`** — Cross-cutting: entities, DTOs, security config, exception handling, audit base

### Within Each Domain Module

Each bounded context follows the same internal structure:

```
<domain>/
├── application/
│   ├── commands/   # Command handlers (write operations)
│   └── queries/    # Query handlers (read operations)
├── domain/service/ # Business logic
├── interfaces/rest/ # REST controllers
├── infrastructure/ # Persistence, schedulers
└── mapper/         # Entity ↔ DTO conversion
```

Commands are dispatched via `CommandBus`. Handlers are annotated with `@IgrpCommandHandler`.

### Key Design Decisions

**Temporary Role Assignment** — `UserRoleAssignment` has a nullable `expiresAt` field. `ExpireRoleService` runs every 60s via `@Scheduled` to remove expired assignments and also schedules per-assignment `TaskScheduler` tasks for precision. `IGRPUserEntity.getRoles()` always filters out expired assignments client-side as well.

**Permission Caching** — `PermissionCacheService` checks permissions via direct JDBC (not JPA) to avoid lazy-load issues in async contexts. Permission cache keys are user/permission combos stored in Redis. Cache is invalidated by `UserRoleChangedEvent` and `RolePermissionChangedEvent`.

**Session Management** — One active session per user enforced at DB level (unique constraint). `SessionCleanupScheduler` expires sessions every 5 min and purges old records daily at 2 AM. Redis is the primary lookup path; DB is the source of truth.

**Audit Trail — Two layers:**
1. Hibernate Envers (`@Audited`) on all entities for schema-level history
2. `SecurityAuditLogEntity` for security-semantic events (LOGIN_SUCCESS, ROLE_EXPIRED, ACCESS_DENIED, etc.). Duplicate login events are suppressed via a 1-hour Caffeine cache keyed on JWT token ID.

**M2M Authentication** — `/api/m2m/**` is served by the same OAuth2 resource-server chain as user endpoints. M2M clients obtain a JWT via the `client_credentials` grant at `/oauth2/token`; both `M2MTokenRejectionFilter` and `SessionEnforcementFilter` skip the `/api/m2m/` prefix so sid-less client_credentials tokens are admitted.

**Superadmin Bypass** — Any user with the `SUPER_ADMIN_ROLE` constant role skips permission checks entirely in `PermissionCacheService`.

**Domain Events** — Handlers publish domain events (`UserRoleChangedEvent`, `RolePermissionChangedEvent`) that are consumed by listeners to invalidate caches. Events are dispatched synchronously by default.

### Entities & Shared Infrastructure

All entities are under `shared/infrastructure/persistence/entity/` and extend `AuditEntity` (provides `createdBy`, `createdDate`, `lastModifiedBy`, `lastModifiedDate`). All entities are marked `@Audited`.

Key entities: `IGRPUserEntity`, `RoleEntity`, `PermissionEntity`, `UserRoleAssignment`, `ApplicationEntity`, `DepartmentEntity`, `SessionEntity`, `SecurityAuditLogEntity`.

Repositories use `JpaSpecificationExecutor` for complex filtered queries and `RevisionRepository` (Envers) for history access.

### Security Configuration

- `OAuth2SecurityConfiguration` — default; validates JWT against `AUTH_JWT_ISSUER` JWKS endpoint
- `BasicAuthSecurityConfiguration` — activated with `basic-auth` Spring profile (dev/test use)
- `MethodSecurityConfig` — enables `@PreAuthorize` with a custom `IgrpMethodSecurityExpressionHandler` that resolves iGRP permission enums from generated classes in `cv.igrp.framework.auth.generated`
- No "ROLE_" prefix convention — authorities are stored as plain role codes

### Error Responses

`GlobalExceptionHandler` returns RFC 7807 `ProblemDetail` format. Custom exceptions use `IgrpResponseStatusException.of()` / `.notFound()` / `.badRequest()` factory methods.

### iGRP Framework Annotations

These are from custom framework JARs pulled from `sonatype.nosi.cv`:
- `@IgrpEntity`, `@IgrpDTO`, `@IgrpController`, `@IgrpPermission` — code-generation markers that trigger framework-level code generation
- `@IgrpCommandHandler` — marks command handler dispatch methods for the CommandBus to discover and route commands

### Testing & Debugging

**Test Organization** — Tests follow command/query handler patterns mirroring the application structure. Run a single test class with `mvn test -Dtest=ClassName`. Tests use JUnit 5 with Spring Boot test fixtures.

**SQL Logging** — Enable with the `development` profile to see all generated SQL:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=development
```
This profile also enables `spring.jpa.hibernate.ddl-auto=update` for automatic schema evolution.

**Database Migrations** — `DatabaseMigrationRunner` (in `shared/config/`) executes cleanup operations at startup via JDBC. For new schema changes, rely on Hibernate's DDL auto in development; custom DDL migrations belong in `DatabaseMigrationRunner.run()`.

### CI/CD

`.gitlab-ci.yml` builds multi-arch (ARM64 + AMD64) Docker images for both JVM and GraalVM native targets. Primary registry: `registry.nosi.cv/igrp/access-management-api`. Pre-release testing uses the `deploy/pre-release` branch.

### Connected Services

PostgreSQL · Redis · MinIO/S3 · Keycloak or WSO2 (external IAM, JWT issuer) · OpenTelemetry Collector · Eureka (optional service discovery)
