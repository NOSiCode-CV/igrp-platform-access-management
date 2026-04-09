---
name: find-or-create-user
description: >
  Use this skill when implementing identity resolution logic in
  igrp-platform-access-management. Activates for tasks involving: resolving
  whether an authenticated user already has an account before creating a new
  one; associating a user by NIC, phone number, email, or external IdP subject
  (sub) across login, invite-send, and invite-accept flows; implementing a
  polymorphic findByAnyIdentifier query; adding nic or phoneNumber handling to
  UpdateUserCommandHandler; or hooking into AuthenticationSuccessEvent to
  enrich user identity from JWT claims at login time. Also activates for
  prompts using terms like "find or create", "user association",
  "multi-identifier", "avoid duplicate user", "link identity", "resolve
  user on login", or "invite-only access", even without mentioning this skill.
---

# Find or Create User — Identity Resolution

## What success looks like

User accounts are created in two ways:
1. **Invite-accept flow** — `RespondUserInvitationCommandHandler` calls
   `resolveOrCreate` when a user accepts a valid invitation. This is the
   primary creation path. The user gets roles assigned.
2. **Authenticated request** — `UserIdentityResolutionListener` calls
   `resolveOrEnrich` on every request. This enriches existing users with
   missing JWT claims (phone, nic, name) but never creates new accounts.

After any scenario, exactly one `t_user` row per person. No duplicates.

```sql
-- Must always return 0 rows
SELECT email, COUNT(*) FROM t_user
WHERE email IS NOT NULL AND email != ''
GROUP BY email HAVING COUNT(*) > 1;

SELECT external_id, COUNT(*) FROM t_user
WHERE external_id IS NOT NULL
GROUP BY external_id HAVING COUNT(*) > 1;
```

---

## Project context

**Base package:** `cv.igrp.platform.access_management`
**Stack:** Spring Boot + Spring Security OAuth2 Resource Server + PostgreSQL + Flyway
**Identity Provider:** Autentika (WSO2 IS 5.11) — OIDC Authorization Code Flow
**No-adapter:** no IAM admin calls. All user state is DB-only.

### Autentika JWT claims by login method

| Method | `acr` | `sub` value | Key claims |
|--------|-------|-------------|------------|
| Email+Password | `pwd` | email (e.g. `user@nosi.cv`) | `email` |
| CMD CV | `cmdcv` | NIC (e.g. `19800408M003H`) | `phone_number` |
| CNI | `cni` | NIC (e.g. `19800408M003H`) | (sub is NIC) |

For CMDCV and CNI, `sub` IS the NIC — there is no separate NIC claim.
For CNI logins, `profile.externalId()` and `profile.nic()` will be the same.

### How the authenticated user is surfaced

`IgrpJwtAuthenticationConverter` (do not modify) converts every JWT into an
`OidcContextAuthenticationToken` whose principal is `IgrpOidcUser`:

```java
if (authentication.getPrincipal() instanceof IgrpOidcUser oidcUser) {
    UserProfile profile = oidcUser.getUserProfile();
    // profile.externalId() — JWT sub, always present
    // profile.email()      — normalized lowercase, "" if absent
    // profile.phone()      — from phone_number claim, "" if absent
    // profile.nic()        — from NIC/sub claim, uppercase, "" if absent
    // profile.authMethod() — "cmdcv", "cni", or "pwd"
    // profile.fullName()   — from name claim
}
```

Always convert `""` → `null` before any lookup or save.

### What already exists — read before writing anything

- `IGRPUserEntity.java` — has `nic` (VARCHAR 13) and `phoneNumber` as fields.
  `username` is NOT NULL UNIQUE.
- `IGRPUserEntityRepository.java` — has `findByExternalId`, `findByUsername`,
  `existsByEmail`. Does NOT have `findByAnyIdentifier` yet.
- `UserIdentifierEntity.java` and repository — exist for secondary lookups.
- `AsyncConfig.java` — `@EnableAsync` already active.
- `InviteUserCommandHandler.java` — must use `pwd`/`cmdcv`/`cni` for
  `allowedAuthMethods`. Do NOT use `CREDENTIALS`/`CMD`/`CNI`.
- `RespondUserInvitationCommandHandler.java` — must use
  `UserIdentityResolutionService.resolveOrCreate()`. Do not create
  `new IGRPUserEntity()` directly.

### Flyway migrations — two files required

**`V1_0_1__add_nic_phone_to_user.sql`:**
```sql
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
CREATE INDEX IF NOT EXISTS idx_user_nic ON t_user (nic) WHERE nic IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_user_phone ON t_user (phone_number)
  WHERE phone_number IS NOT NULL;
ALTER TABLE t_user_aud ADD COLUMN IF NOT EXISTS nic VARCHAR(13);
ALTER TABLE t_user_aud ADD COLUMN IF NOT EXISTS phone_number VARCHAR(32);
```

**`V1_0_2__identity_and_invitation_schema.sql`:**
```sql
ALTER TABLE t_invitation_entity
  ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(50),
  ADD COLUMN IF NOT EXISTS identifier_value VARCHAR(255),
  ADD COLUMN IF NOT EXISTS allowed_auth_methods TEXT;
CREATE TABLE IF NOT EXISTS t_invitation_auth_methods (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    auth_method   VARCHAR(50) NOT NULL
);
CREATE TABLE IF NOT EXISTS t_invitation_roles (
    invitation_id INTEGER NOT NULL REFERENCES t_invitation_entity(id),
    invitation    INTEGER NOT NULL REFERENCES t_role(id)
);
CREATE TABLE IF NOT EXISTS t_user_identifier (
    id               BIGSERIAL PRIMARY KEY,
    user_id          INTEGER NOT NULL REFERENCES t_user(id),
    type             VARCHAR(50) NOT NULL,
    value_normalized VARCHAR(255) NOT NULL,
    verified         BOOLEAN DEFAULT FALSE,
    UNIQUE (type, value_normalized)
);
ALTER TABLE t_security_audit_log
  ADD COLUMN IF NOT EXISTS correlation_id   VARCHAR(255),
  ADD COLUMN IF NOT EXISTS decision_reason  VARCHAR(255),
  ADD COLUMN IF NOT EXISTS request_path     VARCHAR(255);
CREATE TABLE IF NOT EXISTS t_invitation_entity_aud (
    id INTEGER NOT NULL, rev INTEGER NOT NULL, revtype SMALLINT,
    created_by VARCHAR(255), created_date TIMESTAMP,
    last_modified_by VARCHAR(255), last_modified_date TIMESTAMP,
    comments VARCHAR(255), expiry TIMESTAMP, status VARCHAR(255),
    token VARCHAR(255), identifier_type VARCHAR(50),
    identifier_value VARCHAR(255), allowed_auth_methods TEXT,
    PRIMARY KEY (id, rev)
);
```

---

## What to build — five components

### 1. `IGRPUserEntityRepository` — add `findByAnyIdentifier`

One `@Query` method. Every parameter nullable. Use
`(:param IS NULL OR u.field = :param)`. Include `u.status != 'DELETED'`.
Return `Optional<IGRPUserEntity>`. Guard against all-null in the service.

### 2. `UserIdentityResolutionService` — `users.application.service`

Two public methods with different responsibilities:

**`resolveOrCreate(externalId, email, nic, phoneNumber, name)`** —
called only by `RespondUserInvitationCommandHandler`. Finds existing user or
creates new one. Wraps save in `DataIntegrityViolationException` catch with
retry. Returns `IGRPUserEntity`.

**`resolveOrEnrich(externalId, email, nic, phoneNumber, name)`** —
called only by `UserIdentityResolutionListener`. Finds existing user and
enriches null fields. If not found, logs INFO and returns — never creates.
Returns void.

Both use a shared private `enrich()` helper: set field only if currently null
and input non-null. Save only if at least one field changed.

Two `@Async` wrappers: `resolveOrCreateAsync` and `resolveOrEnrichAsync`.

Normalize inputs: lowercase email, uppercase NIC, `""` → `null`.

**Enrichment rules:**

| Field | Rule |
|-------|------|
| `externalId` | Set if null. Never overwrite. |
| `email` | Set if null. Never overwrite. |
| `nic` | Set if null and input non-null. |
| `phoneNumber` | Set if null and input non-null. |
| `name` | Set if null and input non-null. |
| `status`, `roles`, `username` | Never touch. |

### 3. `UserIdentityResolutionListener` — `users.application.listener`

`@Component implements ApplicationListener<AuthenticationSuccessEvent>`.

1. Return if principal is not `IgrpOidcUser` (M2M/basic auth).
2. Extract `UserProfile` synchronously.
3. Convert `""` → `null` for email, nic, phone.
4. Call `identityResolutionService.resolveOrEnrichAsync(...)`.
5. Wrap entire body in try-catch. Never rethrow.

Coexists with `AuthAuditEventListener` — both handle the same event.

### 4. `UpdateUserCommandHandler` — add nic and phoneNumber

After existing `picture`/`signature` blocks:
- If `dto.getNic()` not null/blank → uppercase → set on entity.
- If `dto.getPhoneNumber()` not null/blank → set on entity.

### 5. Fix existing handlers

**`InviteUserCommandHandler`** — `allowedAuthMethods` must match Autentika:
```java
if ("EMAIL".equalsIgnoreCase(type))  allowed.add("pwd");
else if ("PHONE".equalsIgnoreCase(type)) allowed.add("cmdcv");
else if ("NIC".equalsIgnoreCase(type))   allowed.add("cni");
```
Wrap `notificationAdapter.send()` in try-catch — never rethrow.

**`RespondUserInvitationCommandHandler`** — replace direct entity creation:
```java
String externalId = profile.externalId();
String nic = (profile.nic() != null && !profile.nic().isBlank())
             ? profile.nic() : null;
IGRPUserEntity user = userIdentityResolutionService.resolveOrCreate(
    externalId, email, nic, phone, profile.fullName());
```

---

## Gotchas

- **`resolveOrEnrich` never creates. `resolveOrCreate` always may create.**
  Listener → `resolveOrEnrich`. Invite-accept → `resolveOrCreate`. Never swap.

- **`allowedAuthMethods` must match Autentika `acr` exactly.** Use `pwd`,
  `cmdcv`, `cni`. Using `CREDENTIALS`, `CMD`, `CNI` causes 400 on accept.

- **`profile.externalId()` is NOT the nic parameter.** For CMDCV/CNI `sub`
  is the NIC, but pass it as `externalId`. Use `profile.nic()` for `nic`.
  Passing a UUID as `nic` throws `value too long for VARCHAR(13)`.

- **`@Async` self-invocation does not work.** Listener must call `@Async`
  on the service bean, not `this.method()` inside the service.

- **`AuthenticationSuccessEvent` fires on every request**, not just login.
  Enrichment is idempotent — saves only when a field changes.

- **`findByAnyIdentifier` with all-null matches every row.** Guard at
  service level before calling repository.

- **`username` is NOT NULL UNIQUE.** Set `username = externalId` on creation.

- **`t_user_aud` must mirror `t_user`.** Add `nic` and `phone_number` to both
  in V1_0_1 or every save throws 500.

- **`t_invitation_auth_methods`, `t_invitation_roles`, `t_user_identifier`
  must exist.** Missing tables cause `relation does not exist` on invite.

- **`t_security_audit_log` needs `correlation_id`, `decision_reason`,
  `request_path`.** Missing columns abort the invite-accept transaction.

- **`InviteUserCommandHandler` email failure rolls back transaction.**
  `notificationAdapter.send()` must be inside try-catch.

- **`IGRPUserEntity.setNic()` may still write to `username`.** Verify it
  sets `this.nic = nic`, not `this.username = nic`.

---

## Verification

```powershell
.\mvnw.cmd compile -q  # must produce no errors
```

**Scenario A — Invite EMAIL and accept with pwd:**
```
POST /api/users/invite (admin token)
{ "identifierType": "EMAIL", "identifierValue": "user@nosi.cv",
  "departmentCode": "DEPT_X", "roles": ["DEPT_X.member"] }

POST /api/users/invite/response?token=<token> (user token, acr=pwd)
{ "accept": true }  →  200 OK, status=ACCEPTED
```
```sql
SELECT COUNT(*) FROM t_user WHERE email = 'user@nosi.cv'; -- 1
```

**Scenario B — Invite PHONE and accept with CMDCV:**
```
POST /api/users/invite
{ "identifierType": "PHONE", "identifierValue": "+2385162210",
  "departmentCode": "DEPT_X", "roles": ["DEPT_X.member"] }

POST /api/users/invite/response?token=<token> (CMDCV token, acr=cmdcv)
{ "accept": true }  →  200 OK
```
```sql
SELECT phone_number FROM t_user WHERE external_id = '19800408M003H';
-- +2385162210
```

**Scenario C — Enrichment on login:**
```sql
UPDATE t_user SET phone_number = NULL WHERE external_id = '19800408M003H';
```
```
GET /api/users/me  (CMDCV token)
```
```sql
SELECT phone_number FROM t_user WHERE external_id = '19800408M003H';
-- +2385162210  (restored by listener)
```

**Scenario D — No duplicates:**
```sql
SELECT email, COUNT(*) FROM t_user
WHERE email IS NOT NULL AND email != ''
GROUP BY email HAVING COUNT(*) > 1;  -- 0 rows

SELECT external_id, COUNT(*) FROM t_user
WHERE external_id IS NOT NULL
GROUP BY external_id HAVING COUNT(*) > 1;  -- 0 rows
```
