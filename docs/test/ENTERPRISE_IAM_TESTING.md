# Enterprise IAM Testing Guide

This guide validates the enterprise authorization target described in `docs/ENTERPRISE_IAM_RECOMMENDATIONS.md`.

The goal is to prove that:
- permissions follow a fine-grained `module.resource.action` pattern
- authorization remains role-based, department-aware, and deny-by-default
- policy-based authorization is applied where contextual rules are required
- audit logging captures enterprise-grade security context
- permission caching works and invalidates immediately after access changes
- user PII fields such as NIC and phone number are modeled, validated, and protected correctly

---

## Scope

This guide covers the enterprise IAM recommendations through static validation, startup validation, runtime API validation, audit verification, cache verification, and data protection checks.

| Area | Covered here |
| --- | --- |
| Fine-grained permission naming | Yes |
| Hybrid RBAC with department grouping | Yes |
| Active role context behavior | Yes |
| Role-only permission assignment model | Yes |
| ABAC / policy decision pipeline | Yes |
| Enterprise audit logging | Yes |
| Authorization caching and invalidation | Yes |
| Deny-by-default and secure server-side enforcement | Yes |
| PII fields (`nic`, `phoneNumber`) | Yes |
| Static code and DB validation | Yes |

---

## Source Coverage

This guide was derived from the enterprise IAM reference and the current access-management implementation points:
- `docs/ENTERPRISE_IAM_RECOMMENDATIONS.md`
- `docs/TESTING.md`
- `src/main/java/cv/igrp/platform/access_management/shared/security/IgrpAuthorizationService.java`
- `src/main/java/cv/igrp/platform/access_management/authorization/application/commands/handler/SingleCheckAuthorizationHandler.java`
- `src/main/java/cv/igrp/platform/access_management/authorization/domain/service/PermissionCacheService.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/cache/PermissionCacheEvictService.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/cache/CacheEvictionInterceptor.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/service/AuthorizationSyncService.java`
- `src/main/java/cv/igrp/platform/access_management/users/interfaces/rest/UserController.java`
- `src/main/java/cv/igrp/platform/access_management/department/interfaces/rest/DepartmentController.java`
- `src/main/java/cv/igrp/platform/access_management/shared/application/dto/IGRPUserDTO.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/IGRPUserEntity.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/repository/PermissionEntityRepository.java`
- `src/main/java/cv/igrp/platform/access_management/security_audit/application/service/SecurityAuditService.java`
- `src/main/java/cv/igrp/platform/access_management/security_audit/application/service/SecurityAuditServiceImpl.java`
- `src/main/java/cv/igrp/platform/access_management/security_audit/application/service/SecurityAuditContextProvider.java`
- `src/main/java/cv/igrp/platform/access_management/security_audit/domain/entities/SecurityAuditLogEntity.java`

---

## Test Strategy

Use 6 validation layers together:

1. **Static code validation**
   - prove permission naming, authorization entrypoints, policy wiring, audit hooks, and cache invalidation patterns exist in code.

2. **Startup validation**
   - prove permission synchronization and authorization bootstrapping still work after the enterprise changes.

3. **Runtime RBAC validation**
   - prove permissions come only from roles, remain department-scoped, and change with active role context.

4. **Runtime ABAC validation**
   - prove ownership, state, and department-scope policies deny or allow correctly.

5. **Audit and cache validation**
   - prove sensitive events are audited with the required metadata and that permission cache invalidation is immediate.

6. **PII validation**
   - prove `nic` and `phoneNumber` are validated, persisted, and exposed only under the intended rules.

---

## Prerequisites

Set these variables before running the requests:

```bash
BASE_URL=http://localhost:8081
SUPERADMIN_TOKEN=<valid superadmin bearer token>
MANAGER_TOKEN=<valid token with role-management and user-management permissions>
LIMITED_TOKEN=<valid token without the tested privileged permission>
OWNER_TOKEN=<valid token for the user who owns the ABAC-protected resource>
FOREIGN_USER_TOKEN=<valid token for another user in a different department or without ownership>
TEST_USER_ID=<existing managed user id>
TEST_OWNER_USER_ID=<existing owner user id>
TEST_DEPARTMENT_CODE=FINANCE
TEST_OTHER_DEPARTMENT_CODE=HR
TEST_ROLE_CODE=FINANCE_MANAGER
TEST_SECOND_ROLE_CODE=FINANCE_AUDITOR
TEST_PERMISSION_CODE=finance.transactions.approve
TEST_PERMISSION_PREFIX=finance.transactions
TEST_ACTIVE_ROLE_CODE=FINANCE_MANAGER
TEST_SECOND_ACTIVE_ROLE_CODE=FINANCE_AUDITOR
TEST_RESOURCE_ID=<resource id used by policy-based authorization tests>
TEST_DRAFT_RESOURCE_ID=<resource in DRAFT state>
TEST_FINAL_RESOURCE_ID=<resource in non-editable state>
TEST_CORRELATION_ID=enterprise-iam-test-001
TEST_NIC=<check Autentika documents for test CNI>
TEST_PHONE=<check Autentika documents for test phone number>
```

Recommended observability while testing:
- enable application logs at INFO or DEBUG
- enable Redis logs or metrics if possible
- inspect DB state directly for permissions, audit logs, and user fields
- capture request headers, especially correlation-id and user-agent
- keep one SQL client open to inspect `t_permission`, `t_role_permission`, `t_role_users`, `t_user`, and `t_security_audit_log`

### Dependency rule

Run setup and context-establishing scenarios before any scenario that depends on them:

- Confirm static and startup checks first.
- Confirm department and role context before validating user permissions.
- Capture the user active role before switching it.
- Use concrete ABAC-protected endpoints only after the permission gate is confirmed working.
- Run audit and cache checks while logs and Redis are observable.
- Run PII update tests only after confirming the user update flow is available in the target implementation.

### Test data dependency map

| Scenario | Depends on |
| --- | --- |
| T10 Multi-role membership | T00, T01, T02 |
| T11 Active role switch changes effective permissions | T10 |
| T12 Department-scoped role isolation | T10 |
| T13 Deny-by-default permission enforcement | T10 |
| T20 Ownership policy | T10 and one implemented ownership-protected endpoint |
| T21 State policy | T10 and one implemented state-sensitive endpoint |
| T22 Department-scope policy | T10 and one implemented department-scope-protected endpoint |
| T30 Audit denied decision | T13 |
| T31 Audit active role switch | T11 |
| T32 Audit privilege change | one role/permission management operation already executed |
| T40 Cache miss then hit | T10 |
| T41 Subject eviction after active role change | T11 and T40 |
| T42 Role/permission change invalidation | one role-permission update already executed |
| T50 PII create or update | user update flow implemented |
| T51 PII validation failure | T50 request shape known |
| T52 PII restricted exposure | T50 |

---

## Static Validation Scenarios

### T00 — Permission names follow `module.resource.action`

**Purpose**
- Prove the codebase and synchronized permissions use a fine-grained dot-separated naming convention.

**Checks**

Run searches from the project root:

```bash
rg -n "igrp\\.[a-z0-9_]+\\.[a-z0-9_]+" src/main/java docs
rg -n "@PreAuthorize\\(" src/main/java
rg -n "Permission\\." src/main/java
```

Run SQL checks:

```sql
SELECT name
FROM t_permission
WHERE status = 'ACTIVE'
ORDER BY name;

SELECT name
FROM t_permission
WHERE status = 'ACTIVE'
  AND name !~ '^[a-z0-9]+(\\.[a-z0-9_]+){2,}$';
```

**Expected result**
- Enterprise permissions exist in dot notation.
- No active enterprise permission fails the naming regex.
- Permission names do not contain spaces or uppercase letters.

**Enterprise proof**
- Confirms the platform migrated from coarse or inconsistent naming toward a standard enterprise permission vocabulary.

---

### T01 — Authorization uses a single server-side entrypoint and avoids raw role checks

**Purpose**
- Prove authorization remains server-side and routed through the dedicated authorization service rather than hard-coded role checks.

**Checks**

```bash
rg -n "@Service\\(\"igrpAuthorization\"\\)|checkPermission\\(" src/main/java
rg -n "hasRole\\(|hasAuthority\\(" src/main/java
rg -n "\"ROLE_|'ROLE_" src/main/java
```

**Expected result**
- The authorization service entrypoint exists.
- Protected endpoints primarily use the authorization service or a successor enterprise authorization facade.
- There are no new hard-coded business role strings such as `ADMIN`, `MANAGER`, or direct raw role checks in controllers.

**Enterprise proof**
- Confirms centralized enforcement and avoids fragmented authorization logic.

---

### T02 — Permissions are granted only through roles, not directly to users

**Purpose**
- Prove the platform maintains role-only permission attribution.

**Checks**

```bash
rg -n "user.*permission|permission.*user" src/main/java
rg -n "t_user_permission|direct user permission" src/main resources docs
```

Run SQL checks:

```sql
SELECT COUNT(*) AS role_permission_count FROM t_role_permission;
```

**Expected result**
- No direct user-permission assignment table or mapping is introduced for enterprise authorization.
- Effective permissions continue to derive from role membership.

**Enterprise proof**
- Preserves hybrid RBAC with roles as the only permission carrier.

---

### T03 — Policy / ABAC layer exists with a decision pipeline

**Purpose**
- Prove enterprise authorization includes a policy evaluation layer beyond the basic permission gate.

**Checks**

```bash
rg -n "Policy|AuthorizationManager|allow\\('|RequirePermission|RequirePolicy|ACCESS_DENIED|reason" src/main/java
```

**Expected result**
- A policy abstraction, authorization manager, or equivalent ABAC layer exists.
- Code shows a separation between permission check and contextual policy evaluation.
- Structured deny reasons are available for audit.

**Enterprise proof**
- Confirms the system can enforce contextual rules such as ownership, status, and department scope.

---

### T04 — Audit schema and context include enterprise metadata

**Purpose**
- Prove audit logging contains the minimum required security context.

**Checks**

```bash
rg -n "ipAddress|userAgent|contextData|timestamp|ACCESS_DENIED|PROFILE_ACTIVATED|USER_" src/main/java
```

Run SQL checks:

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_name = 't_security_audit_log'
ORDER BY column_name;
```

**Expected result**
- Audit schema includes who, what, when, and where fields.
- Security audit service supports denied access and privilege events.
- Context payload can store structured metadata.

**Enterprise proof**
- Confirms the logging model is suitable for investigation and compliance.

---

### T05 — PII fields exist in DTO, entity, and DB migration artifacts

**Purpose**
- Prove `nic` and `phoneNumber` were implemented as part of enterprise user modeling.

**Checks**

```bash
rg -n "nic|phoneNumber|phone_number" src/main/java src/main/resources
```

Run SQL checks:

```sql
SELECT column_name
FROM information_schema.columns
WHERE table_name = 't_user'
  AND column_name IN ('nic', 'phone_number');
```

**Expected result**
- `IGRPUserDTO`, `IGRPUserEntity`, mappers, and persistence schema include the new fields.
- Validation annotations or equivalent input validation rules are present.

**Enterprise proof**
- Confirms the user model supports the required enterprise identity attributes.

---

## Startup Validation Scenarios

### T06 — Permission synchronization boots with enterprise permission catalog

**Purpose**
- Prove application startup registers or validates the permission catalog successfully.

**Steps**
- Start the application with DB and Redis available.
- Observe logs for authorization synchronization.
- Inspect `t_permission` after startup.

**Expected result**
- Application starts successfully.
- Permission synchronization completes without errors.
- Active permissions include the enterprise naming pattern and no duplicate semantic aliases.

**Enterprise proof**
- Confirms the permission catalog is synchronized and ready for runtime authorization.

---

## Runtime Validation Scenarios

## 1. Hybrid RBAC and active role context

### T10 — Current user exposes multi-role membership

**Purpose**
- Prove a user can hold multiple roles and that roles remain department-scoped.

**Endpoint**
- `GET /api/users/me/roles`

**Auth**
- Bearer token for a user known to have at least two roles in the same or different departments

**Request**

```bash
curl --request GET "{{BASE_URL}}/api/users/me/roles" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}"
```

**Expected response**
- `200 OK`
- body contains a list with at least two role records when the test user is multi-role
- each role includes department context or can be correlated to department context

**DB assertions**
- `t_role_users` contains multiple rows for the same user
- all linked roles are `ACTIVE`

**Enterprise proof**
- Confirms hybrid RBAC is operational through multi-role membership.

---

### T11 — Switching active role changes effective permissions

**Purpose**
- Prove the active role behaves as authorization context selection.

**Endpoints**
- `GET /api/users/me/roles/active`
- `POST /api/users/me/roles/active`
- `GET /api/users/me/permissions`

**Dependencies**
- Run **T10** first

**Request sequence**

```bash
curl --request GET "{{BASE_URL}}/api/users/me/roles/active" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}"
```

```bash
curl --request POST "{{BASE_URL}}/api/users/me/roles/active" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "code": "{{TEST_SECOND_ACTIVE_ROLE_CODE}}",
    "departmentCode": "{{TEST_DEPARTMENT_CODE}}"
  }'
```

```bash
curl --request GET "{{BASE_URL}}/api/users/me/permissions" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}"
```

**Expected response**
- active role changes successfully
- permission list after the switch reflects the new active role context
- permissions available only through the previous active role disappear from the effective set

**DB assertions**
- `t_user.active_role_id` changes to the new role

**Enterprise proof**
- Confirms the system uses active-role context as part of effective authorization.

---

### T12 — Department-scoped access remains isolated

**Purpose**
- Prove a role in one department does not leak permissions into another department context.

**Endpoints**
- `GET /api/users/me/departments/{departmentCode}/roles`
- `GET /api/departments/{departmentCode}/permissions/available`

**Request**

```bash
curl --request GET "{{BASE_URL}}/api/users/me/departments/{{TEST_DEPARTMENT_CODE}}/roles" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}"
```

```bash
curl --request GET "{{BASE_URL}}/api/departments/{{TEST_OTHER_DEPARTMENT_CODE}}/permissions/available" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- user roles are returned only for the requested department
- permissions available in another department do not become effective for the current user unless a role is granted in that department

**DB assertions**
- no cross-department role grant exists unless explicitly assigned

**Enterprise proof**
- Confirms departments remain the grouping boundary for enterprise authorization.

---

### T13 — Missing permission is denied by default

**Purpose**
- Prove authorization denies access when the active role does not grant the required permission.

**Endpoint**
- Use one management endpoint protected by a known permission, for example:
  - `GET /api/users/{{TEST_USER_ID}}/permissions`
  - or another endpoint already guarded with `@PreAuthorize`

**Auth**
- `LIMITED_TOKEN`

**Request**

```bash
curl --request GET "{{BASE_URL}}/api/users/{{TEST_USER_ID}}/permissions" \
  --header "Authorization: Bearer {{LIMITED_TOKEN}}" \
  --header "X-Correlation-Id: {{TEST_CORRELATION_ID}}"
```

**Expected response**
- `403 Forbidden`
- no business mutation occurs

**Audit assertions**
- a denied audit event exists for the request

**Enterprise proof**
- Confirms deny-by-default behavior and server-side enforcement.

---

## 2. ABAC / policy validation

Use these scenarios only after a concrete enterprise ABAC endpoint is implemented. Replace the placeholder endpoint with the real protected route.

### T20 — Ownership policy allows owner and denies non-owner

**Purpose**
- Prove ownership-based ABAC is enforced after the permission gate passes.

**Example target**
- `PUT {{OWNERSHIP_PROTECTED_ENDPOINT}}/{{TEST_RESOURCE_ID}}`

**Owner request**

```bash
curl --request PUT "{{BASE_URL}}/{{OWNERSHIP_PROTECTED_ENDPOINT}}/{{TEST_RESOURCE_ID}}" \
  --header "Authorization: Bearer {{OWNER_TOKEN}}" \
  --header "X-Correlation-Id: {{TEST_CORRELATION_ID}}" \
  --header "Content-Type: application/json" \
  --data '{ "example": "owner update" }'
```

**Non-owner request**

```bash
curl --request PUT "{{BASE_URL}}/{{OWNERSHIP_PROTECTED_ENDPOINT}}/{{TEST_RESOURCE_ID}}" \
  --header "Authorization: Bearer {{FOREIGN_USER_TOKEN}}" \
  --header "X-Correlation-Id: {{TEST_CORRELATION_ID}}" \
  --header "Content-Type: application/json" \
  --data '{ "example": "foreign update" }'
```

**Expected result**
- owner request succeeds
- non-owner request is denied with `403 Forbidden`

**Audit assertions**
- denied event includes an ownership-related reason

**Enterprise proof**
- Confirms contextual authorization is active, not just raw permission membership.

---

### T21 — State policy blocks invalid transitions

**Purpose**
- Prove resource state participates in authorization or mutation approval.

**Example targets**
- `PUT {{STATE_PROTECTED_ENDPOINT}}/{{TEST_DRAFT_RESOURCE_ID}}`
- `PUT {{STATE_PROTECTED_ENDPOINT}}/{{TEST_FINAL_RESOURCE_ID}}`

**Expected result**
- DRAFT resource update is allowed when permission and policy permit it
- non-editable resource update is denied with business or authorization error

**Audit assertions**
- denied event includes a state-related reason

**Enterprise proof**
- Confirms state-aware ABAC behavior.

---

### T22 — Department-scope policy blocks foreign department operations

**Purpose**
- Prove users cannot act outside the department scope enforced by policy.

**Example target**
- `POST {{DEPARTMENT_SCOPE_PROTECTED_ENDPOINT}}`

**Expected result**
- request within the user department scope succeeds
- request against a resource in `{{TEST_OTHER_DEPARTMENT_CODE}}` is denied

**Audit assertions**
- denied event includes a department-scope reason

**Enterprise proof**
- Confirms department boundaries are enforced in contextual authorization.

---

## 3. Audit validation

### T30 — Denied authorization is logged with who, what, where, and why

**Purpose**
- Prove enterprise audit logging captures denied decisions with sufficient metadata.

**Dependencies**
- Run **T13** first

**SQL check**

```sql
SELECT event_type, category, user_id, username, ip_address, user_agent, context_data, timestamp
FROM t_security_audit_log
WHERE event_type = 'ACCESS_DENIED'
ORDER BY timestamp DESC
LIMIT 5;
```

**Expected result**
- latest row includes:
  - user identifier
  - event type
  - timestamp
  - ip address
  - user agent
  - structured context containing permission, path, or denial reason

**Enterprise proof**
- Confirms denied decisions are auditable at investigation quality.

---

### T31 — Active role switch is logged as a privilege-context event

**Purpose**
- Prove profile or active-role changes are auditable.

**Dependencies**
- Run **T11** first

**SQL check**

```sql
SELECT event_type, category, context_data, timestamp
FROM t_security_audit_log
WHERE event_type = 'PROFILE_ACTIVATED'
ORDER BY timestamp DESC
LIMIT 5;
```

**Expected result**
- a `PROFILE_ACTIVATED` event exists
- context identifies the selected role and department

**Enterprise proof**
- Confirms privilege context changes are recorded.

---

### T32 — Privilege changes are logged

**Purpose**
- Prove role assignment, role removal, or permission changes generate security audit events.

**Trigger**
- Execute one privileged operation such as:
  - add role to user
  - remove role from user
  - add permission to role
  - remove permission from role

**Expected result**
- audit log contains a privilege-management event for the operation
- context identifies the actor and target

**Enterprise proof**
- Confirms access configuration changes are auditable.

---

### T33 — Sensitive user field updates are auditable

**Purpose**
- Prove PII changes generate a compact audit summary and can be correlated with Envers.

**Dependencies**
- Run **T50** first

**Expected result**
- security audit event exists for the user update
- Envers revision exists for the same user
- audit context includes enough information to identify that sensitive fields changed without exposing more PII than necessary

**Enterprise proof**
- Confirms compliance-grade tracking for PII changes.

---

## 4. Cache validation

### T40 — Permission evaluation shows cache miss then cache hit

**Purpose**
- Prove permission checks are cached.

**Endpoint**
- any permission-protected read endpoint with no side effects

**Request pattern**
- Call the same protected endpoint twice with the same authenticated subject and active role.

**Expected result**
- first request triggers DB-backed permission resolution
- second request reuses cached decision

**Evidence**
- logs show a miss first and no equivalent DB permission resolution on the second call
- Redis contains a permission cache key for the subject

**Enterprise proof**
- Confirms authorization caching is active.

---

### T41 — Active role change invalidates subject cache immediately

**Purpose**
- Prove a role-context change invalidates the permission cache for the affected user.

**Dependencies**
- Run **T40** first
- Switch active role using **T11**

**Expected result**
- old cached decision does not survive the role switch
- a subsequent protected request recomputes or reloads permissions for the subject

**Evidence**
- Redis key for the subject is removed or replaced
- logs show a new miss after the active role switch

**Enterprise proof**
- Confirms cache invalidation is tied to authorization context changes.

---

### T42 — Role-permission changes invalidate affected users, not only global cache

**Purpose**
- Prove invalidation is immediate and targeted when access configuration changes.

**Trigger**
- add or remove one permission from `{{TEST_ROLE_CODE}}`

**Expected result**
- users holding that role lose or gain the effective permission immediately
- affected users experience a cache miss on next check
- the system does not rely only on full cache eviction

**Enterprise proof**
- Confirms enterprise-ready invalidation behavior for access changes.

---

## 5. PII validation

### T50 — Update user with `nic` and `phoneNumber`

**Purpose**
- Prove enterprise user attributes can be accepted and persisted.

**Endpoint**
- `PUT /api/users/{id}`

**Auth**
- Bearer token with user-update permission

**Request**

```bash
curl --request PUT "{{BASE_URL}}/api/users/{{TEST_USER_ID}}" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}" \
  --header "X-Correlation-Id: {{TEST_CORRELATION_ID}}" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Enterprise Test User",
    "username": "enterprise.user",
    "email": "enterprise.user@example.com",
    "status": "ACTIVE",
    "picture": null,
    "signature": null,
    "nic": "{{TEST_NIC}}",
    "phoneNumber": "{{TEST_PHONE}}"
  }'
```

**Expected response**
- `200 OK`
- returned payload includes the updated fields if the API is designed to return them

**DB assertions**
- `t_user.nic = '{{TEST_NIC}}'`
- `t_user.phone_number = '{{TEST_PHONE}}'`

**Enterprise proof**
- Confirms enterprise identity fields are implemented and writable.

---

### T51 — Invalid NIC or phone format is rejected

**Purpose**
- Prove input validation protects PII data quality.

**Request**

```bash
curl --request PUT "{{BASE_URL}}/api/users/{{TEST_USER_ID}}" \
  --header "Authorization: Bearer {{MANAGER_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Enterprise Test User",
    "username": "enterprise.user",
    "email": "enterprise.user@example.com",
    "status": "ACTIVE",
    "nic": "ABC",
    "phoneNumber": "invalid-phone"
  }'
```

**Expected response**
- `400 Bad Request`
- validation message references the invalid field(s)

**DB assertions**
- stored PII values remain unchanged

**Enterprise proof**
- Confirms validation constraints are enforced.

---

### T52 — PII exposure is restricted

**Purpose**
- Prove PII fields are not leaked to unauthorized callers.

**Request**
- call the user read endpoint with a caller that lacks the dedicated PII-view capability, if such capability was implemented

**Expected result**
- one of these secure outcomes occurs:
  - access is denied
  - PII fields are omitted or masked
  - a dedicated privileged endpoint is required for full PII access

**Enterprise proof**
- Confirms sensitive fields are treated as protected data, not ordinary profile attributes.

---

## 6. Security hygiene validation

### T60 — JWT subject remains the authoritative identity reference

**Purpose**
- Prove subject alignment between JWT `sub`, internal user external id, and authorization checks.

**Checks**
- authenticate as a known user
- call `GET /api/users/me`
- compare JWT `sub` with `t_user.external_id`

**Expected result**
- subject alignment is consistent
- authorization checks evaluate the same subject identity used in persistence

**Enterprise proof**
- Confirms stable identity handling for authorization decisions.

---

### T61 — Tokens are not logged

**Purpose**
- Prove sensitive secrets are not emitted to logs.

**Checks**

```bash
rg -n "Authorization: Bearer|access_token|refresh_token|id_token" logs src/main/java
```

**Expected result**
- no application log prints raw bearer tokens or refresh tokens

**Enterprise proof**
- Confirms secure operational hygiene.

---

## Negative Scenarios

### T70 — Unknown permission code is denied

**Request**
- invoke an authorization path or seeded permission check with a permission code that does not exist

**Expected**
- deny or validation failure

### T71 — Disabled role does not grant permissions

**Request**
- disable the user active role and repeat a formerly allowed protected request

**Expected**
- `403 Forbidden`

### T72 — Inactive user cannot authorize

**Request**
- mark the user inactive and attempt the same protected request

**Expected**
- access denied

### T73 — Cache does not continue to allow after permission removal

**Request**
- remove a required permission from the role and repeat the request after invalidation

**Expected**
- request is denied immediately after the change

---

## Recommended Execution Order

1. Run **T00** to **T05**
2. Run **T06**
3. Validate core RBAC behavior:
   - **T10**
   - **T11**
   - **T12**
   - **T13**
4. Validate ABAC behavior after choosing real enterprise endpoints:
   - **T20**
   - **T21**
   - **T22**
5. Validate audit outcomes:
   - **T30**
   - **T31**
   - **T32**
   - **T33**
6. Validate cache behavior:
   - **T40**
   - **T41**
   - **T42**
7. Validate PII behavior:
   - **T50**
   - **T51**
   - **T52**
8. Run security hygiene and negative scenarios:
   - **T60**
   - **T61**
   - **T70**
   - **T71**
   - **T72**
   - **T73**

---

## Final Acceptance Checklist

The enterprise IAM recommendations are validated only if all of the following are true:

- Permission names follow `module.resource.action`
- Protected endpoints use centralized server-side authorization logic
- Permissions are granted through roles, not direct user overrides
- Multi-role membership works and active role changes effective permissions
- Department boundaries remain part of effective authorization
- Implemented ABAC policies enforce ownership, state, or scope correctly
- Denied decisions are audited with sufficient metadata and reasons
- Privilege changes and active role switches are audited
- Authorization cache produces hits and invalidates immediately after access changes
- Cache invalidation is targeted to affected authorization contexts
- `nic` and `phoneNumber` exist in DTO, entity, and DB storage
- PII input is validated and sensitive exposure is controlled
- Authorization uses JWT `sub` / `external_id` consistently
- Tokens are not written to logs
