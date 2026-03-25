---
name: auth-audit-logging
description: >
  Use this skill when implementing authentication audit logging in
  igrp-platform-access-management. Activates for tasks involving: recording
  login success or failure events from the Autentika/WSO2 OIDC flow into the
  database; tracing which provider method and identifier (CNI or CMD) was used
  per authentication event; querying or exposing auth audit log endpoints; or
  ensuring OWASP ASVS V7.2 compliance for authentication logging. Also activates
  for prompts using terms like "audit trail", "auth events", "login history",
  or "who authenticated when", even without mentioning audit logging explicitly.
---

# Authentication Audit Logging

## What success looks like

After a successful login, the database must contain a row in `auth_audit_log` where
`event_type` is `LOGIN_SUCCESS`, `identifier_type` is `CNI` or `CMD`, `identifier_value`
is a 64-character SHA-256 hex string, `user_id` is the JWT `sub`, and `session_id`
is the JWT `jti`. If any of these are null after a successful login, the implementation
is incorrect.

---

## Project context

**Base package:** `cv.igrp.platform.access_management`
**Stack:** Spring Boot + Spring Security OAuth2 Resource Server + PostgreSQL + Flyway

Autentika (WSO2) issues a JWT → Spring Security validates and fires
`AuthenticationSuccessEvent` → `AuthAuditEventListener` captures synchronously,
builds `AuthAuditContext`, calls async service → `AuthAuditService` hashes the
identifier and persists. The listener is the only entry point.

---

## Autentika JWT claims (exact — wrong names produce null fields)

| Claim | Role |
|---|---|
| `auth_method` | `"CNI"` or `"CMD"` — read this first to pick the identifier |
| `NIC` | Identifier when `auth_method = "CNI"` |
| `phone_number` | Identifier when `auth_method = "CMD"` |
| `sub` | User ID — always present |
| `email` | May be absent |
| `application_code` | IGRP custom claim — may be absent |

---

## Rules

1. All persistence is `@Async` — logging must never block authentication
2. `persist()` has full try-catch, logs `[AUDIT]` prefix on error, **never rethrows**
3. `NIC` and `phone_number` are SHA-256 hashed (64-char hex) before persistence —
   use `java.security.MessageDigest` + `java.util.HexFormat` (stdlib only, no external libs)
4. Never import `IAdapter`
5. Do not create a new `SecurityConfig` or `JwtDecoder`
6. **Do not use Lombok on `AuthAuditLog`.** Use a hand-written static inner `Builder`
   class and `private` all-args constructor. JPA requires a `protected` no-arg
   constructor — add it explicitly. Read `references/AuthAuditLog.java` before
   creating this class.

---

## Implementation

### Before writing any code

```bash
# Next Flyway migration version:
ls src/main/resources/db/migration/ | sort | tail -3

# Is @EnableAsync active?
grep -r "EnableAsync" src/ --include="*.java"
# If empty → add @EnableAsync to IgrpPlatformAccessManagementApplication.java

# Remove any partial files from previous attempts:
find src/ -name "AuthAuditLog.java" -o -name "AuthAuditService.java" | head -5
```

### Flyway migration

Create `src/main/resources/db/migration/V{next}__create_auth_audit_log.sql`:

```sql
CREATE TABLE auth_audit_log (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type       VARCHAR(50)  NOT NULL,
    identifier_type  VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN',
    identifier_value VARCHAR(64),
    user_id          VARCHAR(255),
    application_code VARCHAR(100),
    ip_address       VARCHAR(45),
    user_agent       VARCHAR(512),
    session_id       VARCHAR(255),
    failure_reason   VARCHAR(500),
    timestamp        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    environment      VARCHAR(50),
    CONSTRAINT pk_auth_audit_log PRIMARY KEY (id)
);
CREATE INDEX idx_audit_timestamp        ON auth_audit_log (timestamp DESC);
CREATE INDEX idx_audit_user_timestamp   ON auth_audit_log (user_id, timestamp DESC) WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_identifier_event ON auth_audit_log (identifier_value, event_type) WHERE identifier_value IS NOT NULL;
```

### Classes — create in this order

**Package `shared.domain.audit`:**

1. `IdentifierType` enum: `CNI, CMD, EMAIL, UNKNOWN`
2. `AuthEventType` enum: `LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_INVALID, LOGOUT, IDENTITY_LINKED, IDENTITY_LINK_FAILED, SESSION_EXPIRED`
3. `AuthAuditLog` — read `references/AuthAuditLog.java` before creating this file
4. `AuthAuditContext` record: `(IdentifierType identifierType, String identifierValue, String userId, String applicationCode, String sessionId, HttpServletRequest request)`

**Package `shared.infrastructure.persistence.repository`:**

5. `AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID>` — add `findByUserId(String, Pageable)`

**Package `shared.infrastructure.service`:**

6. `AuthAuditService @Service` — `@Async` methods: `logSuccess(ctx)`, `logFailure(ctx, reason)`, `logEvent(type, ctx)`. Static factory `fromAutentikaJwt(Jwt, HttpServletRequest)` reads `auth_method` first then picks `NIC` or `phone_number`. Static `hash(String)` returns 64-char hex or null.

**Package `shared.infrastructure.security`:**

7. `AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent>` — constructor logs `[AUDIT] AuthAuditEventListener initialized`. Extracts `HttpServletRequest` synchronously before calling async service.
8. `AuthAuditFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent>` — captures exception simple name as reason.

**Package `shared.api.audit`:**

9. `AuthAuditLogDTO` record mirroring `AuthAuditLog` fields
10. `AuthAuditController @RestController @RequestMapping("/api/auth/audit") @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_AUDITOR')")` — GET list (paginated, max 100), GET by id, GET by user

---

## Gotchas

- **`auth_method` is the selector.** A CMD user may also have `NIC` — read `auth_method`
  first, do not infer from claim presence.
- **`RequestContextHolder` is null in `@Async` threads.** Extract `HttpServletRequest`
  in `onApplicationEvent` (synchronous) and pass it into `AuthAuditContext` before
  the async call.
- **`AuthenticationSuccessEvent` fires on every authenticated request** — not only
  at login. Every API call with a valid JWT produces one audit row. This is correct.
- **Failure events arrive before JWT parsing.** `identifierType` is `UNKNOWN` and
  `identifierValue` is null for failure rows — correct by design.
- **Do not use Lombok on `AuthAuditLog`** — read `references/AuthAuditLog.java` instead.

---

## Verification

### Phase 1 — Static

```bash
find src/ -path "*/audit/*.java" | sort
# Expect 10 files: AuthAuditLog, AuthAuditContext, AuthEventType, IdentifierType,
# AuthAuditLogRepository, AuthAuditService, AuthAuditEventListener,
# AuthAuditFailureListener, AuthAuditLogDTO, AuthAuditController

ls src/main/resources/db/migration/ | grep audit
# Expect: V{N}__create_auth_audit_log.sql

grep -r "IAdapter" src/main/java/cv/igrp/platform/access_management/shared/
# Must return: empty

./mvnw compile -q 2>&1 | grep -i "error"
# Must return: empty (warnings about sun.misc.Unsafe are not errors)
```

### Phase 2 — Runtime

Start PostgreSQL: `docker compose up postgres -d`

Start the application and confirm startup log contains:
```
[AUDIT] AuthAuditEventListener initialized
```

```sql
-- Flyway ran successfully
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
-- Expect: V{N}__create_auth_audit_log | success = true

-- After any authenticated request to any protected endpoint
SELECT event_type, identifier_type, length(identifier_value) AS hash_len, user_id, session_id
FROM auth_audit_log ORDER BY timestamp DESC LIMIT 1;
-- Expect: LOGIN_SUCCESS | CNI or CMD | 64 | non-null user_id | non-null session_id

-- Raw identifier never stored
SELECT COUNT(*) FROM auth_audit_log WHERE identifier_value IS NOT NULL AND length(identifier_value) != 64;
-- Must return: 0
```

```bash
# Without token — expect 401
curl -s -o /dev/null -w "%{http_code}" {base_url}/api/auth/audit

# With valid admin token — expect 200
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer {token}" {base_url}/api/auth/audit
```
