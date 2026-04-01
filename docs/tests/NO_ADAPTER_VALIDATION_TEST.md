# No-Adapter Validation Test Guide

This guide validates the feature described in `docs/NO_ADAPTER_ARCHITECTURE.md`.

The goal is to prove that:
- the service no longer depends on `IAdapter`
- the service no longer performs IAM provider admin mutations
- all former adapter-driven business operations now work as DB-only flows
- the only remaining IAM interaction is JWT/OIDC authentication-time validation

---

## Scope

This guide covers every operation that was previously linked to adapter usage:

| Area | Covered here |
| --- | --- |
| Startup bootstrap and synchronization removal | Yes |
| Provider-admin config/dependency removal | Yes |
| Invite user flow | Yes |
| Respond to invitation flow | Yes |
| Department create/update/delete | Yes |
| Role create/delete | Yes |
| Add roles to user | Yes |
| Remove roles from user | Yes |
| User status changes through `UserUtils` | Yes |
| Test code / build static validation | Yes |

---

## Source Coverage

This guide was derived from the current code and the no-adapter architecture reference:
- `docs/NO_ADAPTER_ARCHITECTURE.md`
- `src/main/java/cv/igrp/platform/access_management/IgrpPlatformAccessManagementApplication.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/service/ConfigurationService.java`
- `src/main/java/cv/igrp/platform/access_management/users/interfaces/rest/UserController.java`
- `src/main/java/cv/igrp/platform/access_management/department/interfaces/rest/DepartmentController.java`
- `src/main/java/cv/igrp/platform/access_management/users/application/commands/InviteUserCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/users/application/commands/RespondUserInvitationCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/department/application/commands/PostDepartmentCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/department/application/commands/UpdateDepartmentCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/department/application/commands/DeleteDepartmentCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/department/application/commands/CreateRoleCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/department/application/commands/DeleteRoleCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/users/application/commands/AddRolesToUserCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/users/application/commands/RemoveRolesFromUserCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/users/application/commands/UpdateUserStatusCommandHandler.java`
- `src/main/java/cv/igrp/platform/access_management/shared/infrastructure/utils/UserUtils.java`
- `src/main/resources/application.properties`
- `pom.xml`

---

## Test Strategy

Use 4 validation layers together:

1. **Static code validation**
   - prove there is no `IAdapter` usage, no adapter mocking, and no provider-admin dependency/config.

2. **Startup validation**
   - prove the app starts without adapter wiring and without startup-time provider synchronization.

3. **Runtime API validation**
   - call every endpoint previously connected to adapter usage and verify that DB-only behavior is correct.

4. **Network / log validation**
   - during the runtime scenarios, verify there are no outbound calls to IAM admin endpoints such as:
     - `/admin/`
     - provider management endpoints
     - client-credential token exchange used for admin automation

Allowed IAM interaction:
- OIDC discovery
- JWKS retrieval
- JWT validation

Forbidden IAM interaction:
- provider-side user lookup
- provider-side department/role creation
- provider-side role assignment/unassignment
- provider-side protocol mapper creation

---

## Prerequisites

Set these variables before running the requests:

```bash
BASE_URL=http://localhost:8080
SUPERADMIN_TOKEN=<valid admin bearer token with required permissions>
INVITED_USER_TOKEN=<valid bearer token for the invited person>
INVITED_USER_EMAIL=cariza.dias@nosi.cv
INVITED_USER_SUB=8c6554c9-5b44-4d5f-8b0d-1b65a6a88fd2
REJECTED_INVITE_EMAIL=cariza.reject@nosi.cv
MISMATCH_INVITE_EMAIL=cariza.mismatch@nosi.cv
TEST_DEPARTMENT_CODE=OPS_CORE
TEST_DEPARTMENT_CODE_UPDATED=OPS_CORE_2
TEST_ROLE_CODE=OPS_ANALYST
TEST_ASSIGN_ROLE_CODE=OPS_REVIEWER
TEST_USER_ID=<capture after T11 acceptance>
```

Recommended observability while testing:
- enable application logs at INFO or DEBUG
- capture outbound HTTP calls with a proxy or gateway log
- monitor DB state directly where needed

### Dependency rule

Run setup and creation scenarios before any scenario that depends on their data:

- Create the department first.
- Create the roles after the department exists.
- Create or accept the invitation before using the resulting user in user-role and status tests.
- Use a dedicated second role for add/remove-role validation, so the assignment test does not collide with the role already granted by the invitation flow.
- Use fresh invitation records for:
  - acceptance
  - rejection
  - mismatched-identity validation

### Test data dependency map

| Scenario | Depends on |
| --- | --- |
| T10 Invite accepted-user flow | T20, T30 |
| T11 Accept invitation | T10 |
| T12 Reject invitation | fresh invitation created with same payload pattern as T10, but using `REJECTED_INVITE_EMAIL` |
| T13 Mismatched JWT identity | fresh invitation created with same payload pattern as T10, but using `MISMATCH_INVITE_EMAIL` |
| T30 Create primary role | T20 |
| T32 Create assignment role | T20 |
| T40 Add roles to user | T11, T32 |
| T41 Remove roles from user | T40 |
| T50 Deactivate user | T11 |
| T51 Reactivate user | T50 |
| T31 Delete primary role | T30 and any scenarios that use `TEST_ROLE_CODE` must already be finished |
| T22 Delete department | T31, T32 and all dependent role scenarios must already be finished |

---

## Static Validation Scenarios

### T00 — No adapter imports, mocks, or provider-admin config remain

**Purpose**
- Prove that no business/application code still depends on adapter APIs.

**Checks**

Run searches from the project root:

```bash
rg -n "IAdapter|mock\\(IAdapter\\.class\\)|import cv\\.igrp\\.framework\\.auth\\.core\\.adapter\\.IAdapter" src test
rg -n "igrp\\.keycloak\\." src/main/resources/application.properties
rg -n "keycloak-spring-boot" pom.xml
rg -n "SynchronizationService" src/main/java
```

**Expected result**
- No matches for:
  - `IAdapter`
  - `mock(IAdapter.class)`
  - `igrp.keycloak.*`
  - `keycloak-spring-boot`
  - startup wiring that still invokes synchronization

**No-adapter proof**
- Confirms that adapter code, adapter tests, and provider-admin configuration are gone.

---

### T01 — Application starts without provider-admin dependency

**Purpose**
- Prove startup no longer depends on adapter bootstrap or provider synchronization.

**Steps**
- Start the application with DB and Redis available.
- Do not provide any legacy provider-admin variables.
- Ensure no startup path tries to create departments/roles/protocol mappers in the IAM provider.

**Expected result**
- Application starts successfully.
- Startup logs show normal Spring Boot initialization.
- No log or network evidence of provider-admin reconciliation.

**No-adapter proof**
- Previously, startup bootstrap and synchronization were adapter-driven.
- Now startup must be DB-only.

---

## Runtime Validation Scenarios

## 1. Invitation flow

### T10 — Invite a new user without provider lookup

**Former adapter link**
- `InviteUserCommandHandler`

**Endpoint**
- `POST /api/users/invite`

**Auth**
- Bearer token with user-management permission

**Dependencies**
- Run **T20** first to create `TEST_DEPARTMENT_CODE`
- Run **T30** first to create `TEST_ROLE_CODE`

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/users/invite" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "email": "cariza.dias@nosi.cv",
    "departmentCode": "OPS_CORE",
    "roles": ["OPS_ANALYST"]
  }'
```

**Input rules respected**
- valid email
- existing department code
- existing role code in that department

**Expected response**
- `200 OK`
- body similar to:

```json
{
  "id": 45,
  "email": "cariza.dias@nosi.cv",
  "status": "PENDING",
  "token": "not necessarily returned directly depending on serializer",
  "roles": [
    { "code": "OPS_ANALYST", "description": "..." }
  ]
}
```

**DB assertions**
- A new row exists in `t_invitation_entity`
- `email = 'cariza.dias@nosi.cv'`
- `status = 'PENDING'`
- The invitation is linked to the requested role(s)

**No-adapter proof**
- The request succeeds without any provider-side user resolution.
- No outbound call to provider admin/user lookup endpoints is observed.

---

### T11 — Accept invitation using JWT claims only

**Former adapter link**
- `RespondUserInvitationCommandHandler`

**Endpoint**
- `POST /api/users/invite/response?token={invitationToken}`

**Auth**
- Bearer token for the invited user
- JWT email claim must match invitation email

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/users/invite/response?token={{INVITATION_TOKEN}}" \
  --header "Authorization: Bearer {{INVITED_USER_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "email": "cariza.dias@nosi.cv",
    "accept": true,
    "observation": "Accepted through self-service flow"
  }'
```

**Input rules respected**
- email matches the invitation
- token refers to a pending invitation
- caller is authenticated

**Expected response**
- `200 OK`
- body contains invitation data with `status = "ACCEPTED"`

**DB assertions**
- `t_invitation_entity.status = 'ACCEPTED'`
- a `t_user` row exists for `email = 'cariza.dias@nosi.cv'`
- `t_user.external_id = {{INVITED_USER_SUB}}`
- the user is linked to the invited roles
- capture the created user id and reuse it as `TEST_USER_ID` in **T40**, **T41**, **T50**, and **T51**

**No-adapter proof**
- Acceptance succeeds by reading `sub` and `email` from the JWT.
- No provider user lookup is performed.

---

### T12 — Reject invitation without provider call

**Former adapter link**
- `RespondUserInvitationCommandHandler`

**Endpoint**
- `POST /api/users/invite/response?token={invitationToken}`

**Dependencies**
- Create a fresh invitation first using the same request shape as **T10**, but with:
  - `email = "{{REJECTED_INVITE_EMAIL}}"`
  - the same department and role already created in **T20** and **T30**
- Use the token of that fresh invitation in this scenario

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/users/invite/response?token={{INVITATION_TOKEN}}" \
  --header "Authorization: Bearer {{INVITED_USER_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "email": "{{REJECTED_INVITE_EMAIL}}",
    "accept": false,
    "observation": "Rejected by invited user"
  }'
```

**Expected response**
- `200 OK`
- invitation status becomes `REJECTED`

**DB assertions**
- `t_invitation_entity.status = 'REJECTED'`
- no new user is created if none existed before

**No-adapter proof**
- Rejection remains DB-only and does not attempt provider interaction.

---

### T13 — Reject mismatched JWT identity

**Purpose**
- Prove identity confirmation is now JWT-based rather than provider-lookup-based.

**Dependencies**
- Create a fresh invitation first using the same request shape as **T10**, but with:
  - `email = "{{MISMATCH_INVITE_EMAIL}}"`
  - the same department and role already created in **T20** and **T30**
- Use a JWT whose email claim does not match `{{MISMATCH_INVITE_EMAIL}}`

**Request**
- Repeat **T11**, but use a JWT whose `email` claim does not match the invitation email.

**Expected response**
- `400 Bad Request` or equivalent business error
- invitation remains unchanged

**DB assertions**
- invitation status stays `PENDING`
- no user is created

**No-adapter proof**
- The flow is enforced by JWT claim correlation, not external provider lookup.

---

## 2. Department management

### T20 — Create department (DB-only)

**Former adapter link**
- `PostDepartmentCommandHandler`

**Endpoint**
- `POST /api/departments`

**Auth**
- Bearer token with department-create permission

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/departments" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "code": "OPS_CORE",
    "name": "Operations Core",
    "description": "Operations department",
    "status": "ACTIVE",
    "icon": "Building2",
    "parentCode": null
  }'
```

**Input rules respected**
- `code` matches `^[A-Za-z0-9_-]+$`
- required fields `code` and `name` are present

**Expected response**
- `201 Created`
- response body contains the created department DTO

**DB assertions**
- `t_department.code = 'OPS_CORE'`
- `status = 'ACTIVE'`

**No-adapter proof**
- No department is created in the IAM provider.
- No provider-admin call is emitted.

---

### T21 — Update department (DB-only rename/update)

**Former adapter link**
- `UpdateDepartmentCommandHandler`

**Endpoint**
- `PUT /api/departments/{code}`

**Request**

```bash
curl --request PUT "{{BASE_URL}}/api/departments/OPS_CORE" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "name": "Operations Core Updated",
    "description": "Operations department updated",
    "status": "ACTIVE",
    "icon": "Building2",
    "parentCode": null
  }'
```

**Expected response**
- `200 OK`
- body contains updated department fields

**DB assertions**
- department row exists with updated code/name

**No-adapter proof**
- No provider-side rename is attempted.

---

### T22 — Delete department recursively in DB only

**Former adapter link**
- `DeleteDepartmentCommandHandler`

**Endpoint**
- `DELETE /api/departments/{code}`

**Request**

```bash
curl --request DELETE "{{BASE_URL}}/api/departments/OPS_CORE" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- `204 No Content`

**DB assertions**
- target department status becomes `DELETED`
- child departments, if any, also become `DELETED`
- department roles are marked `DELETED`

**No-adapter proof**
- The deletion is represented only in DB state.
- No provider-side group/role deletion is attempted.

---

## 3. Role management

### T30 — Create role in a department (DB-only)

**Former adapter link**
- `CreateRoleCommandHandler`

**Endpoint**
- `POST /api/departments/{departmentCode}/roles`

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/departments/OPS_CORE/roles" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "code": "OPS_ANALYST",
    "name": "Operations Analyst",
    "description": "Can review operational requests",
    "departmentCode": "OPS_CORE",
    "parentCode": null,
    "status": "ACTIVE",
    "permissions": []
  }'
```

**Input rules respected**
- role code matches `^[A-Za-z0-9_.-]+$`
- department exists

**Expected response**
- `201 Created`
- body contains created role DTO

**DB assertions**
- `t_role.code = 'OPS_ANALYST'`
- `t_role.department = OPS_CORE`
- `status = 'ACTIVE'`

**No-adapter proof**
- No provider-side role creation occurs.

---

### T32 — Create a second role dedicated to add/remove-role validation

**Purpose**
- Ensure the add/remove-role scenarios use a role that was not already granted by invitation acceptance.

**Former adapter link**
- `CreateRoleCommandHandler`

**Endpoint**
- `POST /api/departments/{departmentCode}/roles`

**Dependencies**
- Run **T20** first

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/departments/OPS_CORE/roles" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '{
    "code": "OPS_REVIEWER",
    "name": "Operations Reviewer",
    "description": "Role dedicated to add/remove-role testing",
    "departmentCode": "OPS_CORE",
    "parentCode": null,
    "status": "ACTIVE",
    "permissions": []
  }'
```

**Expected response**
- `201 Created`

**DB assertions**
- `t_role.code = 'OPS_REVIEWER'`
- `t_role.department = OPS_CORE`

**No-adapter proof**
- The role is created only in DB and is available for **T40** and **T41**.

---

### T31 — Delete role in DB only

**Former adapter link**
- `DeleteRoleCommandHandler`

**Endpoint**
- `DELETE /api/departments/{departmentCode}/roles/{roleCode}`

**Request**

```bash
curl --request DELETE "{{BASE_URL}}/api/departments/OPS_CORE/roles/OPS_ANALYST" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- `204 No Content`

**DB assertions**
- role status becomes `DELETED`
- child roles, if any, are also logically deleted according to handler behavior

**No-adapter proof**
- No provider-side role removal occurs.

---

## 4. User-role assignment

### T40 — Add roles to user without provider assignment

**Former adapter link**
- `AddRolesToUserCommandHandler`

**Endpoint**
- `POST /api/users/{id}/departments/{departmentCode}/roles`

**Dependencies**
- Run **T11** first and capture `TEST_USER_ID`
- Run **T32** first to create `TEST_ASSIGN_ROLE_CODE`

**Request**

```bash
curl --request POST "{{BASE_URL}}/api/users/{{TEST_USER_ID}}/departments/OPS_CORE/roles" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '[
    "OPS_REVIEWER"
  ]'
```

**Input rules respected**
- user exists
- department exists
- role exists in that department
- role is not already assigned through the invitation acceptance flow

**Expected response**
- `201 Created`
- body is a list of assigned role DTOs

**DB assertions**
- user-role relationship exists in DB
- follow-up `GET /api/users/{{TEST_USER_ID}}/roles` includes the assigned role

**No-adapter proof**
- No provider-side role assignment occurs.
- No compensation/rollback against provider is needed.

---

### T41 — Remove roles from user without provider unassignment

**Former adapter link**
- `RemoveRolesFromUserCommandHandler`

**Endpoint**
- `DELETE /api/users/{id}/departments/{departmentCode}/roles`

**Dependencies**
- Run **T40** first so the role is already assigned

**Request**

```bash
curl --request DELETE "{{BASE_URL}}/api/users/{{TEST_USER_ID}}/departments/OPS_CORE/roles" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}" \
  --header "Content-Type: application/json" \
  --data '[
    "OPS_REVIEWER"
  ]'
```

**Expected response**
- `200 OK`
- body is the remaining user roles for that department

**DB assertions**
- removed role relationship no longer exists in DB

**No-adapter proof**
- No provider-side unassignment occurs.

---

## 5. User status change / `UserUtils`

### T50 — Deactivate user without provider-side role stripping

**Former adapter link**
- `UserUtils.handleRoleAssignmentsOnStatusChange`
- `UpdateUserStatusCommandHandler`

**Endpoint**
- `PUT /api/users/{id}/status?value=INACTIVE`

**Request**

```bash
curl --request PUT "{{BASE_URL}}/api/users/{{TEST_USER_ID}}/status?value=INACTIVE" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- `200 OK`
- body contains the updated `IGRPUserDTO`
- `status = "INACTIVE"`

**DB assertions**
- `t_user.status = 'INACTIVE'`
- existing role relations remain stored in DB unless current business logic explicitly changes them

**No-adapter proof**
- No provider-side removal of user roles occurs.

---

### T51 — Reactivate user without provider-side role restore

**Former adapter link**
- `UserUtils.handleRoleAssignmentsOnStatusChange`
- `UpdateUserStatusCommandHandler`

**Endpoint**
- `PUT /api/users/{id}/status?value=ACTIVE`

**Request**

```bash
curl --request PUT "{{BASE_URL}}/api/users/{{TEST_USER_ID}}/status?value=ACTIVE" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- `200 OK`
- body contains the updated `IGRPUserDTO`
- `status = "ACTIVE"`

**DB assertions**
- `t_user.status = 'ACTIVE'`
- no provider-side role restoration occurs

**No-adapter proof**
- Status change is now DB-only.

---

## Control Scenario

### T60 — JWT-authenticated control request still works

**Purpose**
- Prove the allowed IAM interaction still works: JWT validation.

**Endpoint**
- `GET /api/users/me`

**Request**

```bash
curl --request GET "{{BASE_URL}}/api/users/me" \
  --header "Authorization: Bearer {{SUPERADMIN_TOKEN}}"
```

**Expected response**
- `200 OK`
- valid current-user payload

**Meaning**
- JWT/OIDC validation still works
- no-adapter did not break authentication

---

## Negative Business-Rule Scenarios

These are not additional adapter link points, but they help validate that business rules still hold after the refactor.

### T70 — Invite existing user

**Request**
- repeat **T10** for an email that already exists in `t_user`

**Expected**
- `409 Conflict`

### T71 — Create department with invalid code

**Request**

```json
{
  "code": "OPS CORE",
  "name": "Invalid Department"
}
```

**Expected**
- `400 Bad Request`

### T72 — Create role in missing department

**Request**
- repeat **T30** using a department code that does not exist

**Expected**
- `404 Not Found`

### T73 — Respond to invitation with mismatched email

**Request**
- repeat **T11** using a payload email different from the invitation email or JWT claim

**Expected**
- `400 Bad Request`

---

## Recommended Execution Order

1. Run **T00** and **T01**
2. Run **T60** to confirm JWT auth still works
3. Create dependent domain data first:
   - **T20**
   - **T30**
   - **T32**
4. Run invitation flow:
   - **T10**
   - **T11**
   - capture `TEST_USER_ID` from the accepted-user record created in **T11**
   - **T12**
   - **T13**
5. Run the remaining DB-only business scenarios that depend on the created data:
   - **T40**
   - **T41**
   - **T50**
   - **T51**
   - **T21**
   - **T31**
   - **T22**
6. Run negative business-rule scenarios

---

## Final Acceptance Checklist

The no-adapter feature is validated only if all of the following are true:

- No code imports or mocks `IAdapter`
- No provider-admin configuration remains in `application.properties`
- No provider-admin dependency remains in `pom.xml`
- Application starts without startup-time provider synchronization
- Invite and invitation response flows work with DB + JWT claims only
- Department CRUD under this scope works with DB-only effects
- Role CRUD under this scope works with DB-only effects
- User-role assignment/removal works with DB-only effects
- User status changes do not trigger provider-side role mutations
- Runtime traffic shows no calls to provider admin endpoints
- JWT-authenticated requests still work normally
