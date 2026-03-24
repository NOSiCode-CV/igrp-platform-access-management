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

After a successful login, the database must contain a row in `auth_audit_log` where:
- `event_type` is `LOGIN_SUCCESS`
- `identifier_type` is `CNI` or `CMD` — the provider method used in this authentication
- `identifier_value` is a 64-character SHA-256 hex string of the identifier (`NIC` or `phone_number`)
- `user_id` is the JWT `sub` claim
- `session_id` is the JWT `jti` claim
- `ip_address` is populated from the HTTP request

If any of these are null after a successful login, the implementation is incorrect.

---

## Project context

**Base package:** `cv.igrp.platform.access_management`
**Stack:** Spring Boot + Spring Security OAuth2 Resource Server + PostgreSQL + Flyway

**How the auth flow works and where this feature fits:**
Autentika (WSO2) issues a JWT → Spring Security validates the token and fires `AuthenticationSuccessEvent` → the listener (`AuthAuditEventListener`) captures the event synchronously, extracts claims and HTTP context, builds an `AuthAuditContext`, and calls the async service → `AuthAuditService` hashes the identifier and persists the row. The listener is the only entry point — do not intercept the auth flow anywhere else.

---

## Autentika JWT claims (exact names — wrong names produce null fields)

| Claim | Type | Role |
|---|---|---|
| `auth_method` | `"CNI"` or `"CMD"` | Selector — read this first to decide which identifier claim to use |
| `NIC` | String | Identifier when `auth_method = "CNI"` |
| `phone_number` | String | Identifier when `auth_method = "CMD"` |
| `sub` | String | User ID — always present |
| `email` | String | May be absent for CNI-only users |
| `application_code` | String | IGRP custom claim — may be absent |

Do not use `cni_number`, `mobile`, or any other variant. These do not exist in Autentika JWTs.

---

## Rules

1. All persistence calls are `@Async` — logging must never block or slow an authentication response
2. `persist()` wraps `repository.save()` in try-catch, logs at ERROR with `[AUDIT]` prefix, and **never rethrows** — a DB failure must not produce a 500 or deny access
3. `NIC` and `phone_number` are always SHA-256 hashed (64-char hex) before persistence — never store raw values
4. Never import or reference `IAdapter` — it is being removed from this project
5. Do not create a new `SecurityConfig` or `JwtDecoder` — `OAuth2SecurityConfiguration.java` already exists and must not be touched

---

## Implementation

### Before writing any code

```bash
# What is the next Flyway migration version?
ls src/main/resources/db/migration/ | sort | tail -3

# Is @EnableAsync already active?
grep -r "EnableAsync" src/ --include="*.java"
```

If `@EnableAsync` is missing, add it to `IgrpPlatformAccessManagementApplication.java` before creating any other file.

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
COMMENT ON COLUMN auth_audit_log.identifier_value IS 'SHA-256 hash of NIC or phone_number. Never plaintext.';
COMMENT ON COLUMN auth_audit_log.session_id       IS 'JWT jti — correlates events from the same session.';
```

### Classes — create in this exact order

**Step 1 — `shared.domain.audit`** (no external dependencies)

1. `IdentifierType` enum: `CNI, CMD, EMAIL, UNKNOWN`
   — `CNI` ← claim `NIC` | `CMD` ← claim `phone_number`
2. `AuthEventType` enum: `LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_INVALID, LOGOUT, IDENTITY_LINKED, IDENTITY_LINK_FAILED, SESSION_EXPIRED`
3. `AuthAuditLog` — JPA `@Entity` mapping to `auth_audit_log`, immutable (Builder pattern, no setters, `protected` no-arg constructor for JPA)
4. `AuthAuditContext` — Java `record`: `(IdentifierType identifierType, String identifierValue, String userId, String applicationCode, String sessionId, HttpServletRequest request)`

**Step 2 — `shared.infrastructure.persistence.repository`** (depends on Step 1)

5. `AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID>`
   — Add: `Page<AuthAuditLog> findByUserId(String userId, Pageable pageable)`

**Step 3 — `shared.infrastructure.service`** (depends on Steps 1–2)

6. `AuthAuditService` — `@Service`
   - `@Async` public methods: `logSuccess(ctx)`, `logFailure(ctx, reason)`, `logEvent(type, ctx)`
   - `persist(type, ctx, reason)` — private, full try-catch, `[AUDIT]` prefix on errors, never rethrows
   - `fromAutentikaJwt(Jwt, HttpServletRequest)` — static factory: reads `auth_method` first, then reads `NIC` or `phone_number` accordingly, uses `jwt.getSubject()` as userId and `jwt.getId()` as sessionId
   - `hash(String)` — static, SHA-256, 64-char hex output, returns null if input is null or blank

**Step 4 — `shared.infrastructure.security`** (depends on Steps 1–3)

7. `AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent>` — `@Component`
   - Constructor logs: `[AUDIT] AuthAuditEventListener initialized`
   - In `onApplicationEvent`: cast to `JwtAuthenticationToken`, extract `Jwt`, call `extractRequest()` **synchronously here — before calling any async method**, build `AuthAuditContext`, then call `auditService.logSuccess(ctx)`
8. `AuthAuditFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent>` — `@Component`
   - Captures `exception.getClass().getSimpleName()` as failure reason, calls `auditService.logFailure(ctx, reason)`

**Step 5 — `shared.api.audit`** (depends on Steps 1–2)

9. `AuthAuditLogDTO` — Java `record` mirroring `AuthAuditLog` fields
10. `AuthAuditController` — `@RestController @RequestMapping("/api/auth/audit") @PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_AUDITOR')")`
    - `GET /api/auth/audit` — paginated, cap size at 100
    - `GET /api/auth/audit/{id}` — single entry by UUID
    - `GET /api/auth/audit/user/{uid}` — all events for a user, paginated

---

## Gotchas

- **`auth_method` is the selector, not the identifier.** Always read `auth_method` first to decide which claim to use. A CMD user may also have a `NIC` claim present in the JWT — if you infer from claim presence you will pick the wrong one.

- **`RequestContextHolder` is thread-local.** It returns null inside `@Async` methods because they run on a different thread. Extract `HttpServletRequest` inside `onApplicationEvent` (which runs synchronously) and pass it into `AuthAuditContext` before the async call. If the request is null inside the service, the extraction happened too late.

- **`AuthenticationSuccessEvent` fires on every authenticated request, not only at login.** This is the correct behaviour for an OAuth2 resource server — every API call with a valid JWT produces this event. Each call produces one audit row, which is the intended design for a complete auth trail.

- **Failure events arrive before JWT parsing completes.** For `AbstractAuthenticationFailureEvent`, `identifierType` is `UNKNOWN` and `identifierValue` is null — this is correct. Do not attempt to read JWT claims from these events.

- **`hash()` receives null for failure events.** The method must handle null/blank input gracefully and return null. The `identifier_value` column accepts null — this is by design.

---

## Verification

Run these in sequence after implementation to confirm everything works:

```bash
# 1. Migration ran successfully
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;
# Expect: V{N}__create_auth_audit_log | success = true

# 2. Listener started — check application startup logs for:
# [AUDIT] AuthAuditEventListener initialized

# 3. After a login — confirm provider method and identifier are captured
SELECT event_type, identifier_type, length(identifier_value) AS hash_len, user_id, session_id
FROM auth_audit_log ORDER BY timestamp DESC LIMIT 1;
# Expect: LOGIN_SUCCESS | CNI or CMD | 64 | <sub value> | <jti value>

# 4. Raw identifier is never stored
SELECT COUNT(*) FROM auth_audit_log WHERE identifier_value = '19800408M003H';
# Must return: 0

# 5. API access control
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/audit
# Expect: 401

curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer {ADMIN_TOKEN}" http://localhost:8080/api/auth/audit
# Expect: 200

# 6. No IAdapter in new files
grep -r "IAdapter" src/main/java/cv/igrp/platform/access_management/shared/
# Must return: empty
```
