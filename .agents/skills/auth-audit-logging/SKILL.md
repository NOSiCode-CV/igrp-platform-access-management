---
name: auth-audit-logging
description: >
  Use this skill when implementing authentication audit logging in
  igrp-platform-access-management. Activates for tasks involving: recording
  login success or failure events from the Autentika/WSO2 OIDC flow into the
  database; tracing which authentication method (CNI, CMDCV, or email/password)
  was used per authentication event; querying or exposing auth audit log
  endpoints; or ensuring OWASP ASVS V7.2 compliance for authentication logging.
  Also activates for prompts using terms like "audit trail", "auth events",
  "login history", "who authenticated when", or "refactor fromAutentikaJwt",
  even without mentioning audit logging explicitly.
---

# Authentication Audit Logging

## What success looks like

After a successful login, the database must contain a row in `auth_audit_log` where:

- `event_type` = `LOGIN_SUCCESS`
- `identifier_type` = `CNI`, `CMDCV`, or `EMAIL` — never null or UNKNOWN for a valid login
- `identifier_value` = 64-character SHA-256 hex string — never null for a valid login
- `user_id` = JWT `sub`
- `session_id` = JWT `jti`

---

## Project context

**Base package:** `cv.igrp.platform.access_management`
**Stack:** Spring Boot + Spring Security OAuth2 Resource Server + PostgreSQL + Flyway

Autentika (WSO2 IS 5.11) issues JWTs → Spring Security validates the **access token**
and fires `AuthenticationSuccessEvent` → `AuthAuditEventListener` captures synchronously,
builds `AuthAuditContext`, calls async service → `AuthAuditService` hashes the identifier
and persists. The listener is the only entry point.

---

## JWT claim strategy

Autentika does **not** use a custom `auth_method` claim. The access token carries
standard OIDC claims `amr` and `acr` which together identify the authentication method.

### amr claim (List\<String\>)

| amr value                    | Meaning                                                           |
| ---------------------------- | ----------------------------------------------------------------- |
| `BasicAuthenticator`         | Email + Password login                                            |
| `OpenIDConnectAuthenticator` | CMDCV or CNI — use `acr` to distinguish                           |
| `refresh_token`              | Token renewal — no fresh auth method, use claim presence fallback |
| absent or empty              | Unknown origin — use claim presence fallback                      |

### acr claim (String) — distinguishes CMDCV from CNI

| acr value                                     | Meaning                                                     |
| --------------------------------------------- | ----------------------------------------------------------- |
| `cmdcv`                                       | Chave Móvel Digital de Cabo Verde → identifier_type = CMDCV |
| anything else (including the string `"null"`) | CNI → identifier_type = CNI                                 |

### Identifier claim per method

| Method                                 | identifier_type | Claim to hash  | Fallback if claim absent |
| -------------------------------------- | --------------- | -------------- | ------------------------ |
| BasicAuthenticator                     | EMAIL           | `email`        | UNKNOWN                  |
| OpenIDConnectAuthenticator + acr=cmdcv | CMDCV           | `phone_number` | CMDCV with null value    |
| OpenIDConnectAuthenticator + acr≠cmdcv | CNI             | `NIC`          | CNI with null value      |

### Full claim reference

| Claim              | Type           | Hashed        | Stored as                     |
| ------------------ | -------------- | ------------- | ----------------------------- |
| `amr`              | `List<String>` | No            | — (used for detection only)   |
| `acr`              | `String`       | No            | — (used for detection only)   |
| `phone_number`     | `String`       | Yes (SHA-256) | `identifier_value` when CMDCV |
| `NIC`              | `String`       | Yes (SHA-256) | `identifier_value` when CNI   |
| `email`            | `String`       | Yes (SHA-256) | `identifier_value` when EMAIL |
| `sub`              | `String`       | **No**        | `user_id` as-is               |
| `jti`              | `String`       | **No**        | `session_id` as-is            |
| `application_code` | `String`       | No            | `application_code` as-is      |

**Detection order in `fromAutentikaJwt()`:**

1. Read `amr` as `List<String>` — never as plain String
2. If `amr` contains `BasicAuthenticator` → EMAIL, use `email` claim
3. If `amr` contains `OpenIDConnectAuthenticator`:
   - Read `acr`
   - If `acr` equals `cmdcv` (case-insensitive) → CMDCV, use `phone_number`
   - Otherwise → CNI, use `NIC`
4. If `amr` absent → fall back to claim presence: `phone_number` → CMDCV,
   `NIC` → CNI, `email` → EMAIL, else UNKNOWN

---

## Rules

1. All persistence is `@Async` — logging must never block or delay authentication
2. `persist()` wraps `repository.save()` in try-catch, logs `[AUDIT]` prefix on error,
   **never rethrows** — a DB failure must not deny access or produce a 500
3. `NIC`, `phone_number`, and `email` are SHA-256 hashed (64-char hex) before persistence
   — use `java.security.MessageDigest` + `java.util.HexFormat` (stdlib only, no Guava)
4. `sub` and `jti` are stored as-is — do not hash them
5. Never import `IAdapter`
6. Do not create a new `SecurityConfig` or `JwtDecoder`
7. **Do not use Lombok on `AuthAuditLog`** — read `references/AuthAuditLog.java` first
   and use that Builder pattern exactly

---

## Implementation

### Before writing any code

```bash
# Confirm next Flyway migration version
ls src/main/resources/db/migration/ | sort | tail -3

# Confirm @EnableAsync is active
grep -r "EnableAsync" src/ --include="*.java"
# If empty → add @EnableAsync to the main application class

# Check for partial files from previous attempts
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
CREATE INDEX idx_audit_user_timestamp   ON auth_audit_log (user_id, timestamp DESC)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_identifier_event ON auth_audit_log (identifier_value, event_type)
    WHERE identifier_value IS NOT NULL;
```

### Classes — create in dependency order

**Package `shared.domain.audit`:**

1. `IdentifierType` enum: `CNI, CMDCV, EMAIL, UNKNOWN`
2. `AuthEventType` enum: `LOGIN_SUCCESS, LOGIN_FAILURE, TOKEN_INVALID, LOGOUT, IDENTITY_LINKED, IDENTITY_LINK_FAILED, SESSION_EXPIRED`
3. `AuthAuditLog` entity — read `references/AuthAuditLog.java` first, use that Builder
4. `AuthAuditContext` record: `(IdentifierType identifierType, String identifierValue, String userId, String applicationCode, String sessionId, HttpServletRequest request)`

**Package `shared.infrastructure.persistence.repository`:**

5. `AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID>` —
   add `Page<AuthAuditLog> findByUserId(String userId, Pageable pageable)`

**Package `shared.infrastructure.service`:**

6. `AuthAuditService @Service`:
   - `@Async void logSuccess(AuthAuditContext ctx)`
   - `@Async void logFailure(AuthAuditContext ctx, String reason)`
   - `@Async void logEvent(AuthEventType type, AuthAuditContext ctx)`
   - `static AuthAuditContext fromAutentikaJwt(Jwt jwt, HttpServletRequest request)` —
     implements the detection order described in the JWT claim strategy section
   - `static String hash(String value)` — SHA-256 hex, returns null if input is
     null or blank

**Package `shared.infrastructure.security`:**

7. `AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent>` —
   constructor logs `[AUDIT] AuthAuditEventListener initialized`. Extracts
   `HttpServletRequest` synchronously in `onApplicationEvent` before any async call.
8. `AuthAuditFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent>` —
   uses `exception.getClass().getSimpleName()` as `failureReason`.

**Package `shared.api.audit`:**

9. `AuthAuditLogDTO` record mirroring all `AuthAuditLog` fields
10. `AuthAuditController @RestController @RequestMapping("/api/auth/audit")`
    `@PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_AUDITOR')")`:
    - `GET /api/auth/audit` — paginated, max page size 100
    - `GET /api/auth/audit/{id}` — by UUID
    - `GET /api/auth/audit/user/{userId}` — all events for a user, paginated

---

## Gotchas

- **`amr` is a JSON array, not a String.** Use `jwt.getClaimAsStringList("amr")`.
  `jwt.getClaimAsString("amr")` returns the literal string `"[BasicAuthenticator]"` —
  `.contains("BasicAuthenticator")` on that string returns `false`.

- **`amr = ["refresh_token"]` is not an authentication method.** When a token is
  obtained via refresh, Autentika sets `amr: ["refresh_token"]` with no `acr` and
  no identity claims (`phone_number`, `NIC` are absent). Do not try to read `acr`
  in this case — treat it as the absent-amr fallback and use claim presence detection.

- **`acr = "null"` is the string "null", not a Java null.** Autentika sets
  `acr: "null"` (string) for non-CMDCV OIDC flows. Use case-insensitive equals against
  `"cmdcv"` — this handles both Java null and the string "null" correctly.

- **`acr` determines the method type, not claim presence.** If `acr = "cmdcv"` but
  `phone_number` is absent, `identifier_type` is still `CMDCV` and `identifier_value` is
  null

- **`RequestContextHolder` is null in `@Async` threads.** Extract `HttpServletRequest`
  synchronously in `onApplicationEvent` and pass it through `AuthAuditContext`.

- **`AuthenticationSuccessEvent` fires on every authenticated request**, not only at
  login. Every API call with a valid JWT produces one audit row. This is correct.

- **Failure events arrive before JWT parsing.** `identifierType` = `UNKNOWN` and
  `identifierValue` = null for `LOGIN_FAILURE` rows — correct by design.

- **Do not use Lombok on `AuthAuditLog`.** It causes JPA constructor conflicts.
  Use `references/AuthAuditLog.java` instead.

---

## Verification

### Phase 1 — Static

```bash
find src/ -path "*/audit/*.java" | sort
# Expect 10 files

ls src/main/resources/db/migration/ | grep audit
# Expect: V{N}__create_auth_audit_log.sql

grep -r "IAdapter" src/main/java/cv/igrp/platform/access_management/shared/
# Must return: empty

./mvnw compile -q 2>&1 | grep -i "error"
# Must return: empty
```

### Phase 2 — Runtime

Start the application. Confirm startup log contains:

```
[AUDIT] AuthAuditEventListener initialized
```

```sql
-- Migration ran
SELECT version, description, success FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 3;

-- After BasicAuthenticator request
-- Expect: LOGIN_SUCCESS | EMAIL | 64 | non-null user_id | non-null session_id

-- After CMDCV request (acr = cmdcv)
-- Expect: LOGIN_SUCCESS | CMDCV | 64 | non-null user_id | non-null session_id

-- After CNI request (amr = OpenIDConnectAuthenticator, acr != cmdcv)
-- Expect: LOGIN_SUCCESS | CNI | 64 | non-null user_id | non-null session_id

SELECT event_type, identifier_type, length(identifier_value) AS hash_len,
       user_id, session_id
FROM auth_audit_log ORDER BY timestamp DESC LIMIT 5;

-- Raw identifier never stored
SELECT COUNT(*) FROM auth_audit_log
WHERE identifier_value IS NOT NULL AND length(identifier_value) != 64;
-- Must return: 0
```

```bash
curl -s -o /dev/null -w "%{http_code}" {base_url}/api/auth/audit
# Expect: 401

curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer {token}" {base_url}/api/auth/audit
# Expect: 200
```
