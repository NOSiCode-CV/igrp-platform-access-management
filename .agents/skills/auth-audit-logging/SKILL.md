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
- `user_id` = JWT `sub` as-is — never hashed
- `session_id` = JWT `jti` as-is — never hashed

---

## Project context

**Base package:** `cv.igrp.platform.access_management`
**Stack:** Spring Boot + Spring Security OAuth2 Resource Server + PostgreSQL + Flyway

Autentika (WSO2 IS 5.11) issues JWTs → Spring Security validates the **access token**
and fires `AuthenticationSuccessEvent` → `AuthAuditEventListener` captures synchronously,
builds `AuthAuditContext`, calls async service → `AuthAuditService` hashes the identifier
and persists. The listener is the only entry point.

---

## Authentication method detection — confirmed claim mapping

The three login methods and their exact JWT claim signatures:

| Login method | `amr` value | `acr` value |
|---|---|---|
| Email + Password | `BasicAuthenticator` | `pwd` |
| Chave Móvel Digital CV | `OpenIDConnectAuthenticator` | `cmdcv` |
| Cartão Nacional de Identificação | `OpenIDConnectAuthenticator` | `cni` |

These are the only valid combinations. Any other `amr`/`acr` combination → `UNKNOWN`.

### Column mapping per method

| Method | `identifier_type` | `identifier_value` | `user_id` |
|---|---|---|---|
| BasicAuthenticator / pwd | `EMAIL` | SHA-256(`email` claim) | `sub` |
| OpenIDConnectAuthenticator / cmdcv | `CMDCV` | SHA-256(`phone_number` claim) | `sub` |
| OpenIDConnectAuthenticator / cni | `CNI` | SHA-256(`sub`) | `sub` |

**`user_id` is always `sub`** — the IdP's stable identifier for the user regardless
of login method. Never hashed. Enables querying all events for a user across methods.

**CNI has no separate NIC claim.** The NIC is the value of `sub` for OIDC logins.
For CNI: `identifier_value = SHA-256(sub)`, `user_id = sub` as-is.

**`identifier_value` enables privacy-safe search.** To find all audit events for a
given phone number or NIC, compute SHA-256 of the value and query
`WHERE identifier_type = 'CMDCV' AND identifier_value = <hash>`. Raw values are
never stored — only the hash.

### Full claim reference

| Claim | Type | Role | Stored as |
|---|---|---|---|
| `amr` | `List<String>` | Method category — detection only | not stored |
| `acr` | `String` | Exact method discriminator — detection only | not stored |
| `sub` | `String` | Universal user ID | `user_id` as-is; also hashed → `identifier_value` for CNI |
| `email` | `String` | Email address | SHA-256 → `identifier_value` for EMAIL |
| `phone_number` | `String` | Mobile number including country prefix | SHA-256 → `identifier_value` for CMDCV |
| `jti` | `String` | JWT ID | `session_id` as-is |
| `application_code` | `String` | IGRP custom claim | `application_code` as-is |

### Detection order in `fromAutentikaJwt()`

```
1. Read amr as List<String> — never as plain String
2. If amr contains "BasicAuthenticator" AND acr == "pwd"
   → EMAIL
   → identifier_value = SHA-256(email claim)
   → user_id = sub
3. If amr contains "OpenIDConnectAuthenticator":
   a. acr == "cmdcv"
      → CMDCV
      → identifier_value = SHA-256(phone_number claim)
      → user_id = sub
   b. acr == "cni"
      → CNI
      → identifier_value = SHA-256(sub)
      → user_id = sub
   c. acr is any other value → UNKNOWN, identifier_value = null
4. If amr contains "refresh_token" OR amr is absent/empty
   → claim presence fallback:
     phone_number present → CMDCV, identifier_value = SHA-256(phone_number)
     email present        → EMAIL, identifier_value = SHA-256(email)
     else                 → UNKNOWN, identifier_value = null
   → user_id = sub in all fallback cases
```

---

## Rules

1. All persistence is `@Async` — logging must never block or delay authentication
2. `persist()` wraps `repository.save()` in try-catch, logs `[AUDIT]` prefix on
   error, **never rethrows** — a DB failure must not deny access or produce a 500
3. `email`, `phone_number`, and `sub` (for CNI) are SHA-256 hashed (64-char hex)
   before storing as `identifier_value` — use `java.security.MessageDigest` +
   `java.util.HexFormat` (stdlib only, no Guava)
4. `sub` stored as `user_id` is **never hashed**
5. `jti` stored as `session_id` is **never hashed**
6. Never import `IAdapter`
7. Do not create a new `SecurityConfig` or `JwtDecoder`
8. **Do not use Lombok on `AuthAuditLog`** — read `references/AuthAuditLog.java`
   first and use that Builder pattern exactly
9. `IdentifierType` enum values: `CNI`, `CMDCV`, `EMAIL`, `UNKNOWN`

---

## Implementation

### Before writing any code

```bash
# Confirm next Flyway migration version
ls src/main/resources/db/migration/ | sort | tail -3

# Confirm @EnableAsync is active
grep -r "EnableAsync" src/ --include="*.java"
# If empty → add @EnableAsync to the main application class

# Confirm IdentifierType uses CMDCV not CMD
grep -r "IdentifierType" src/ --include="*.java" | grep "CMD[^V]"
# Must return: empty

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
    CONSTRAINT pk_auth_audit_log           PRIMARY KEY (id),
    CONSTRAINT auth_audit_log_identifier_type_check
        CHECK (identifier_type IN ('CNI', 'CMDCV', 'EMAIL', 'UNKNOWN'))
);
CREATE INDEX idx_audit_timestamp        ON auth_audit_log (timestamp DESC);
CREATE INDEX idx_audit_user_timestamp   ON auth_audit_log (user_id, timestamp DESC)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_audit_identifier_event ON auth_audit_log (identifier_value, event_type)
    WHERE identifier_value IS NOT NULL;
```

### Classes — create in this order

**Package `shared.domain.audit`:**

1. `IdentifierType` enum — values: `CNI`, `CMDCV`, `EMAIL`, `UNKNOWN`

2. `AuthEventType` enum — values: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `TOKEN_INVALID`,
   `LOGOUT`, `IDENTITY_LINKED`, `IDENTITY_LINK_FAILED`, `SESSION_EXPIRED`

3. `AuthAuditLog` — **read `references/AuthAuditLog.java` before creating this file**,
   use that Builder pattern exactly. Do not use Lombok.

4. `AuthAuditContext` record:
   ```
   (IdentifierType identifierType, String identifierValue, String userId,
   String applicationCode, String sessionId, HttpServletRequest request)
   ```

**Package `shared.infrastructure.persistence.repository`:**

5. `AuthAuditLogRepository extends JpaRepository<AuthAuditLog, UUID>` —
   add `Page<AuthAuditLog> findByUserId(String userId, Pageable pageable)`

**Package `shared.infrastructure.service`:**

6. `AuthAuditService @Service` with:
   - `@Async void logSuccess(AuthAuditContext ctx)`
   - `@Async void logFailure(AuthAuditContext ctx, String reason)`
   - `@Async void logEvent(AuthEventType type, AuthAuditContext ctx)`
   - `static AuthAuditContext fromAutentikaJwt(Jwt jwt, HttpServletRequest request)`
     — implements the detection order in the claim mapping section above
   - `static String hash(String value)` — SHA-256 hex, returns null if input is
     null or blank

**Package `shared.infrastructure.security`:**

7. `AuthAuditEventListener implements ApplicationListener<AuthenticationSuccessEvent>`:
   - Constructor logs `[AUDIT] AuthAuditEventListener initialized`
   - Extracts `HttpServletRequest` synchronously in `onApplicationEvent` via
     `RequestContextHolder` before calling any async method
   - Only processes `JwtAuthenticationToken` instances

8. `AuthAuditFailureListener implements ApplicationListener<AbstractAuthenticationFailureEvent>`:
   - Extracts `HttpServletRequest` synchronously
   - Uses `exception.getClass().getSimpleName()` as `failureReason`
   - Calls `authAuditService.logFailure()` with `UNKNOWN` identifierType and null value

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
  `jwt.getClaimAsString("amr")` returns the literal `"[BasicAuthenticator]"` —
  `.contains()` on that string returns false.

- **`acr` values are exact known strings.** `"pwd"`, `"cmdcv"`, `"cni"` — use
  case-insensitive equals. There is no fallback logic within
  `OpenIDConnectAuthenticator` flows — if `acr` is not `cmdcv` or `cni`, it is
  `UNKNOWN`.

- **CNI has no separate NIC claim.** The NIC is `sub`. Hash `sub` as
  `identifier_value` for CNI. Do not look for a `NIC` claim — it does not exist.

- **`user_id` is always `sub`, never hashed.** Even for CNI where `sub` is also
  the source of `identifier_value`, store `user_id = sub` as-is.

- **`phone_number` includes the country code prefix** (e.g. `+239...`). Hash it
  exactly as it appears in the claim — do not strip the `+` or modify it.

- **`amr = ["refresh_token"]` has no `acr` and no identity claims.** Treat it
  as the absent-amr fallback. Do not attempt to read `acr` in this case.

- **`RequestContextHolder` is null in `@Async` threads.** Extract
  `HttpServletRequest` synchronously in `onApplicationEvent` before any async call
  and pass it through `AuthAuditContext`.

- **`AuthenticationSuccessEvent` fires on every authenticated request**, not only
  at login. Every API call with a valid JWT produces one audit row. This is correct.

- **Failure events arrive before JWT parsing.** `identifierType` = `UNKNOWN` and
  `identifierValue` = null for `LOGIN_FAILURE` rows — correct by design.

- **Do not use Lombok on `AuthAuditLog`.** It causes JPA constructor conflicts at
  runtime. Use the hand-written Builder from `references/AuthAuditLog.java`.

- **The check constraint must list `CMDCV` not `CMD`.** If the table was created
  with `CMD` in the constraint, drop and recreate it before inserting:
  ```sql
  ALTER TABLE auth_audit_log DROP CONSTRAINT auth_audit_log_identifier_type_check;
  UPDATE auth_audit_log SET identifier_type = 'CMDCV' WHERE identifier_type = 'CMD';
  ALTER TABLE auth_audit_log ADD CONSTRAINT auth_audit_log_identifier_type_check
      CHECK (identifier_type IN ('CNI', 'CMDCV', 'EMAIL', 'UNKNOWN'));
  ```

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

grep -r "IdentifierType.CMD[^V]" src/ --include="*.java"
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
-- Flyway ran successfully
SELECT version, description, success FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 3;

-- After EMAIL login (amr=BasicAuthenticator, acr=pwd)
-- Expect: EMAIL | 64 | user_id = email value from sub
SELECT identifier_type, length(identifier_value) AS hash_len, user_id, session_id
FROM auth_audit_log ORDER BY timestamp DESC LIMIT 1;

-- After CMDCV login (amr=OpenIDConnectAuthenticator, acr=cmdcv)
-- Expect: CMDCV | 64 | user_id = NIC from sub

-- After CNI login (amr=OpenIDConnectAuthenticator, acr=cni)
-- Expect: CNI | 64 | user_id = NIC from sub
-- identifier_value = SHA-256(user_id) for CNI

-- Search all events for a given phone number:
SELECT * FROM auth_audit_log
WHERE identifier_type = 'CMDCV'
  AND identifier_value = '<sha256-of-phone-number>';

-- Raw identifier never stored
SELECT COUNT(*) FROM auth_audit_log
WHERE identifier_value IS NOT NULL AND length(identifier_value) != 64;
-- Must return: 0
```

```bash
# Without token — expect 401
curl -s -o /dev/null -w "%{http_code}" {base_url}/api/auth/audit

# With valid admin token — expect 200
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer {token}" {base_url}/api/auth/audit
```
