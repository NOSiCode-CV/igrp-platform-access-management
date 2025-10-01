# iGRP 3.0 Access Management — End-to-End Test Plan (beta version)

**Scope:** validate the platform rules for Departments, Applications, Menus, Resources, Roles, Permissions and Users.

**Important notes:**

* All commands assume:

    * `{{BASE_URL}}` is set (e.g. `http://localhost:8080`).
    * `{{TOKEN}}` is a valid Bearer token for the user performing the check or the admin performing attribution.
* We keep a **clear separation** between:

    * **Attribution endpoints** (manage access — assigning departments, roles, permissions, sharing).
    * **Usage / Check endpoints** (endpoints used by runtime checks or UI to fetch menus/resources — RBAC + ABAC).
* The test plan includes both **attribution** tests (manage relationships) and **check** tests (simulate runtime authorization).

---

## Quick mapping — attribution vs usage (examples)

| Concern                 |                                                                                                           Attribution (manage) endpoints | Usage / Check endpoints                                             |
| ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------: | :------------------------------------------------------------------ |
| Departments / ownership | `POST /api/departments` `PUT /api/departments/{id}` `GET /api/departments/:code/...` `GET /api/departments/:code/applications/available` | `GET /api/departments/:code/applications` (listing assigned)        |
| Applications            |                                 `POST /api/applications` `POST /api/applications/:code/departments` `POST /api/applications/:code/roles` | `GET /api/applications` `GET /api/applications/:id/menus/available` |
| Menus                   |                                                `POST /api/menus` `POST /api/menus/:code/addRoles` `POST /api/menus/:code/addDepartments` | `GET /api/menus/app/:appCode` (get app menus for user)              |
| Resources & Items       |                                      `POST /api/resources` `POST /api/resources/:name/add-items` `POST /api/resources/:code/departments` | `GET /api/resources/:name` `GET /api/resource-items/:id`            |
| Roles                   |                                              `POST /api/roles` `POST /api/roles/{name}/addPermissions` `POST /api/roles/{name}/addUsers` | `GET /api/roles`                                                    |
| Permissions             |                                             `POST /api/permissions` `GET /api/permissions` `GET /api/roles/{name}/permissions/available` | `POST /api/authorize/check` `POST /api/authorize/batch-check`       |
| Users                   |                                                                                  `POST /api/users` `POST /api/users/{username}/addRoles` | `GET /api/users/{username}`                                         |

(Exact endpoint names and payloads come from the Postman collection and API docs.  )

---

## Prerequisites (one-time)

1. Load the Postman collection (you already have it).
2. Set environment variables:

    * `BASE_URL` = `http://localhost:8080` (or your server)
    * `TOKEN` = valid bearer token for an admin account (used to call attribution endpoints)
    * For runtime checks, provide user tokens containing `roles` claims when needed.
3. Ensure the system DB is clean (or use separate names/codes used in tests to avoid collisions).

---

## Test data naming convention (used across tests)

* Departments:

    * Parent: `DEPT_FINANCE` (name: "Finance")
    * Child: `DEPT_PAYROLL` (name: "Payroll", parent = Finance)
    * Other: `DEPT_LOGISTICS`
* Applications:

    * `APP_FIN` (Finance app)
    * `APP_LOG` (Logistics app)
* Menus:

    * `MENU_SALARY` (Salaries)
    * `MENU_REPORTS` (Reports)
* Resources:

    * `RES_FIN_API` (Finance API)
    * Resource items: `RI_SALARY_LIST`, `RI_BUDGET_REPORT`
* Roles:

    * `ROLE_FIN_MGR` (Finance Manager) — department Finance
    * `ROLE_JR_ACCOUNT` (Junior Accountant) — parent = ROLE_FIN_MGR
    * `ROLE_HR_MGR` (HR Manager) — department Payroll (for menu example)
* Permissions:

    * `PERM_VIEW_SALARY`, `PERM_VIEW_BUDGET`
* Users:

    * `alice` (will have ROLE_FIN_MGR)
    * `bob` (will have ROLE_JR_ACCOUNT)
    * `carol` (no roles initially)

---

## Setup (step-by-step attribution calls)

> Run these in order to prepare the system for the test scenarios.

### 1) Create departments (attribution)

* Endpoint: `POST /api/departments`
* Example payload:

```json
{
  "code": "DEPT_FINANCE",
  "name": "Finance",
  "description": "Finance department",
  "status": "ACTIVE"
}
```

* Create child Payroll:

```json
{
  "code": "DEPT_PAYROLL",
  "name": "Payroll",
  "description": "Payroll dept (child of Finance)",
  "parent_code": "DEPT_FINANCE",
  "status": "ACTIVE"
}
```

* Expected status: `201 Created`, body contains created Department DTO.

**Note:** retrieve department IDs by `GET /api/departments?code=DEPT_FINANCE` when needed. (Attribution endpoint.)

### 2) Create applications

* Endpoint: `POST /api/applications`
* Example payload: `APP_FIN`

```json
{
  "code": "APP_FIN",
  "name": "Finance Application",
  "description": "Finance app",
  "status": "ACTIVE",
  "type": "SYSTEM",
  "departments": ["DEPT_IGRP"] // this is just because a department is required at app creation, we add iGRP one so we can test add department endpoint later
}
```

* Expected status: `201 Created`.

### 3) Add application to parent department (attribution)

* Endpoint: `POST /api/applications/:code/departments`
* Example: add `APP_FIN` to `DEPT_FINANCE`

    * Path: `/api/applications/APP_FIN/departments`
    * Body:

```json
{ "codes": ["DEPT_FINANCE"] }
```

* Expected: `200 OK`. After this, Finance department has access to APP_FIN.

### 4) Create roles (department-scoped)

* Endpoint: `POST /api/roles`
* Example `ROLE_FIN_MGR`:

```json
{
  "name": "ROLE_FIN_MGR",
  "description": "Finance Manager",
  "departmentCode": "DEPT_FINANCE",
  "status": "ACTIVE"
}
```

* Create child role `ROLE_JR_ACCOUNT` (parent role = `ROLE_FIN_MGR`):

```json
{
  "name": "ROLE_JR_ACCOUNT",
  "description": "Junior Accountant",
  "departmentCode": "DEPT_FINANCE",
  "parentName": "DEPT_FINANCE.ROLE_FIN_MGR",
  "status": "ACTIVE"
}
```

* Create role `ROLE_HR_MGR` in `DEPT_PAYROLL`:

```json
{
  "name": "ROLE_HR_MGR",
  "departmentCode": "DEPT_PAYROLL",
  "status": "ACTIVE"
}
```

* Expected: `201 Created` for each.

### 5) Create permissions

* Endpoint: `POST /api/permissions`
* Example:

```json
{
  "name": "PERM_VIEW_SALARY",
  "description": "View salary",
  "status": "ACTIVE",
  "applicationCode": "APP_FIN",
  "departmentCode": "DEPT_FINANCE"
}
```

* `PERM_VIEW_BUDGET` similar.
* Expected: `201 Created`.

### 6) Assign permissions to roles (attribution)

* Endpoint: `POST /api/roles/{name}/addPermissions`
* Example: grant `DEPT_FINANCE.PERM_VIEW_SALARY` to `DEPT_FINANCE.ROLE_FIN_MGR`

    * Path: `/api/roles/DEPT_FINANCE.ROLE_FIN_MGR/addPermissions`
    * Body:

```json
[ "DEPT_FINANCE.PERM_VIEW_SALARY" ]
```

* Expected: `200 OK`. Role `DEPT_FINANCE.ROLE_FIN_MGR` now has permission.

### 7) Create menus & link to application

* Endpoint: `POST /api/menus` (create)
* Example `MENU_SALARY`:

```json
{
  "name": "Salaries",
  "code": "MENU_SALARY",
  "applicationCode": "APP_FIN",
  "pageSlug": "salaries",
  "type": "MENU_PAGE",
  "position": 1,
  "status": "ACTIVE"
}
```

* Expected: `201 Created`.

### 8) Link menu to role (menus are role-based) — **Attribution**

* Endpoint: `POST /api/menus/:code/addRoles`
* Example: link `MENU_SALARY` to `ROLE_HR_MGR` (or `ROLE_FIN_MGR`)

    * Path: `/api/menus/MENU_SALARY/addRoles`
    * Body:

```json
[ "DEPT_PAYROLL.ROLE_HR_MGR" ]
```

* Expected: `200 OK`. Now the menu is visible to users that have that role (subject to department sharing).

### 9) Create resources and resource items

* Create resource:

    * `POST /api/resources`
    * Body:

```json
{
  "name": "RES_FIN_API",
  "type": "API",
  "description": "Finance API",
  "applications": ["APP_FIN"],
  "status": "ACTIVE"
}
```

* Create items (resource items):

    * `POST /api/resources/RES_FIN_API/items` or the endpoint the collection uses (see postman). Body examples:

```json
[
  {
    "name": "RI_SALARY_LIST",
    "description": "Salary List endpoint",
    "permissionName": "DEPT_FINANCE.PERM_VIEW_SALARY",
    "resourceName": "RES_FIN_API",
    "url": "/api/finance/salary-list"
  }
]
```

* Expected: `200 OK` or `201 Created` depending on implementation.

### 10) Add roles to application (so role users can access the app)

* Endpoint: `POST /api/applications/:code/roles`
* Example:

```json
{ "codes": ["ROLE_FIN_MGR"] }
```

* Expected: `200 OK`.

### 11) Create users and assign roles

* Endpoint: `POST /api/users` (create user) or use existing users
* Assign roles:

    * `POST /api/users/{username}/addRoles`
    * Body:

```json
[ "ROLE_FIN_MGR" ]
```

* Expected: `200 OK`.

---

# Per-rule test cases

For each of the 7 rules below we provide **Success**, **Forbid** and **Error** test cases. Each test shows endpoint, method, sample request, expected response and HTTP status code. Attribution endpoints are marked.

---

## 1) Department (Top-level access)

**Rule:** child dept can only be attributed access to apps/menus/resources that its parent already has access to — *except* when the child creates a new element within itself; in that case the parent inherits it.

### Test 1.1 — Success (parent→child inheritance attribution)

* **Purpose:** Add existing application `APP_FIN` to child `DEPT_PAYROLL` when parent `DEPT_FINANCE` already has the app.
* **Attribution endpoint:** `POST /api/applications/APP_FIN/departments`
* **Body:** `{"codes":["DEPT_PAYROLL"]}`
* **Expected result:** `200 OK`, body showing `APP_FIN` assigned to `DEPT_PAYROLL`.
* **Why success:** Parent had it, so child can be attributed.

### Test 1.2 — Forbid (cannot attribute existing app to child when parent lacks it)

* **Purpose:** Try to add `APP_LOG` (Logistics) to `DEPT_PAYROLL` while `DEPT_FINANCE` does not have `APP_LOG`.
* **Attribution endpoint:** `POST /api/applications/APP_LOG/departments`
* **Body:** `{"codes":["DEPT_PAYROLL"]}`
* **Expected result:** `403 Forbidden` (or `400` business error with clear message). Body:

```json
{ "message": "Parent department DEPT_FINANCE lacks access to APP_LOG. Child cannot be attributed." }
```

### Test 1.3 — Success (child creates new app and parent inherits)

* **Purpose:** Child `DEPT_PAYROLL` creates a *new* application (allowed); parent will inherit this app.
* **Attribution endpoint:** `POST /api/applications` (create)
* **Body:**

```json
{
  "code": "APP_PAYROLL_CHILD",
  "name": "Payroll Child App",
  "description": "A child app for payroll department",
  "departments": ["DEPT_PAYROLL"],
  "status": "ACTIVE",
  "type": "INTERNAL"
}
```

* **Expected result:** `201 Created`. After that `GET /api/departments/DEPT_FINANCE/applications` should show `APP_PAYROLL_CHILD` (parent inherited).
* **Why success:** The "create-in-child → parent inherit" rule is allowed.

### Test 1.4 — Error (invalid department code)

* **Endpoint:** `POST /api/applications/APP_FIN/departments`
* **Body:** `{"codes":["DEPT_DOES_NOT_EXIST"]}`
* **Expected:** `400 Bad Request` or `404 Not Found` with error body:

```json
{ "message": "Department DEPT_DOES_NOT_EXIST not found" }
```

---

## 2) Applications (department + role controlled)

**Rule:** Applications belong to one or more departments; access requires department + role mapping.

### Test 2.1 — Success (user with role and department access can list app menus)

* **Goal:** `alice` has `ROLE_FIN_MGR` and `DEPT_FINANCE` has `APP_FIN`. When alice requests menus, she should see them.
* **Usage endpoint (get app menus):** `GET {{BASE_URL}}/api/menus/app/APP_FIN` with `Authorization: Bearer {{alice-token}}`
* **Expected:** `200 OK` with list of Menu DTOs that `ROLE_FIN_MGR` is permitted to see (only menus linked to roles alice has).
* **Example response snippet:**

```json
[
  { "code":"MENU_SALARY","name":"Salaries","roles":["ROLE_FIN_MGR"] }
]
```

### Test 2.2 — Forbid (user in other department but without role)

* **Goal:** `carol` (no roles) tries `GET /api/menus/app/APP_FIN`.
* **Expected:** `200 OK` with an empty list OR `403` depending on runtime design (but preferred: empty list). If endpoint enforces roles at preauthorization, then `403 Forbidden`.
* **Check to validate:** Ensure the response is empty list when no roles match.

### Test 2.3 — Error (application code does not exist)

* **Endpoint:** `GET /api/menus/app/APP_DOES_NOT_EXIST`
* **Expected:** `404 Not Found`.

---

## 3) Menus (role-based only)

**Rule:** Menu visibility and access are controlled only by roles (RBAC). Menus are attributed to roles and to departments (sharing), not to standalone permissions.

### Test 3.1 — Success (user has role attached to menu)

* **Setup (attribution):** `POST /api/menus/MENU_SALARY/addRoles` body `["ROLE_HR_MGR"]`
* **Usage:** `GET /api/menus/app/APP_FIN` with `Authorization: Bearer token-of-user-with-ROLE_HR_MGR`
* **Expected:** `200 OK` with `MENU_SALARY` present.

### Test 3.2 — Forbid (user lacks role)

* **Usage:** same GET for a user without `ROLE_HR_MGR`.
* **Expected:** `200 OK` with `MENU_SALARY` not present (or `403` if preauthorized). Test should assert the menu is absent.

### Test 3.3 — Error (bad menu code in addition)

* **Endpoint:** `POST /api/menus/NO_SUCH_MENU/addRoles`
* **Body:** `["ROLE_FIN_MGR"]`
* **Expected:** `404 Not Found` or `400` with message.

---

## 4) Resources (role + permission control; ABAC option)

**Rule:** Resources are controlled by roles and/or permissions. Resource items have a single permission each (system supports one permission per resource item). For ABAC checks, the system calls Access Management/Permission check by permission name.

### Test 4.1 — Success (RBAC-based access via role)

* **Setup:** `ROLE_FIN_MGR` has `PERM_VIEW_SALARY` and resource item `RI_SALARY_LIST` is linked to `PERM_VIEW_SALARY`.
* **Usage (RBAC check):** `POST /api/authorize/check` with `Authorization: Bearer alice-token`

    * Body:

```json
{ "resource": "RI_SALARY_LIST", "action": "VIEW" }
```

* **Expected:** `200 OK` response body: `{ "allowed": true, "viaRoles":["ROLE_FIN_MGR"], "reason": null }`

### Test 4.2 — Success (ABAC check via permission name)

* **Usage (ABAC):** make the same `POST /api/authorize/check` but ensure the check path triggers the ABAC flow calling the access-management client with `"PERM_VIEW_SALARY"` (server does this internally). The request is identical; expected same result: `allowed: true`.
* **Note:** The test validates the server uses the permission name in the ABAC call.

### Test 4.3 — Forbid (user lacks both role and permission)

* **User:** `carol` with no roles.
* **POST /api/authorize/check** with the same body:
* **Expected:** `200 OK` with `{ "allowed": false, "viaRoles": [], "reason": "no role or permission" }` OR `403` depending on your runtime.

### Test 4.4 — Error (resource item configured with unknown permission)

* **Setup:** resource item `RI_BROKEN` referencing permission `PERM_DOES_NOT_EXIST`.
* **Call:** `POST /api/authorize/check`
* **Expected:** `400 Bad Request` with message: `permission PERM_DOES_NOT_EXIST is not configured` or `404 resource/permission`.

---

## 5) Roles (department-specific and recursive)

**Rule:** Roles belong to a single department and can be hierarchical. Child roles can be attributed only to permissions and users that the parent role already has access to (unless the child creates a new permission that parent will inherit).

### Test 5.1 — Success (parent → child inherits)

* **Setup:** `ROLE_FIN_MGR` has `PERM_VIEW_SALARY`.
* **Action:** create child `ROLE_JR_ACCOUNT` with parent `ROLE_FIN_MGR` and attempt to add `PERM_VIEW_SALARY` to child via `POST /api/roles/ROLE_JR_ACCOUNT/addPermissions`.
* **Expected:** `200 OK`. Child can be assigned the same permission.

### Test 5.2 — Forbid (child cannot gain permission outside parent unless created locally)

* **Action:** try to add `PERM_ADMIN_ONLY` (that parent does not have) to `ROLE_JR_ACCOUNT` using `POST /api/roles/ROLE_JR_ACCOUNT/addPermissions`.
* **Expected:** `403 Forbidden` (unless the child is creating a *new* permission — see next test). Body:

```json
{ "message": "Cannot attribute permission PERM_ADMIN_ONLY to child role - parent role lacks it" }
```

### Test 5.3 — Success (child creates a new permission; parent should inherit)

* **Action:** create new permission `PERM_XPTO` and assign it to `ROLE_JR_ACCOUNT`.

    * `POST /api/permissions` → `PERM_XPTO`
    * `POST /api/roles/ROLE_JR_ACCOUNT/addPermissions` → `["PERM_XPTO"]`
* **Expected:** `200 OK`. After assignment, check parent `ROLE_FIN_MGR` permissions should include `PERM_XPTO` (parent inherited).

### Test 5.4 — Error (role in wrong department)

* **Action:** try to create role `ROLE_WRONG` with `department` set to `DEPT_DOES_NOT_EXIST`.
* **Expected:** `400 Bad Request` / `404 Not Found`.

---

## 6) Permissions (exist standalone or linked)

**Rule:** Permissions may be standalone, linked to resources, roles, or both. Server should support checking by permission name (ABAC) or via role membership (RBAC). Only one permission per resource item is supported.

### Test 6.1 — Success (permission exists and is linked to resource)

* **Check:** `GET /api/permissions?departmentCode=DEPT_FINANCE`
* **Expected:** `200 OK` and `PERM_VIEW_SALARY` present.

### Test 6.2 — Forbid (permission exists but is inactive)

* **Setup:** set `PERM_VIEW_BUDGET` status to `INACTIVE`.
* **Call:** `POST /api/authorize/check` for `RI_BUDGET_REPORT`
* **Expected:** `200 OK` with `allowed:false` and reason indicating inactive permission.

### Test 6.3 — Error (permission name missing from request)

* **Call:** `POST /api/authorize/check` with body `{ "action":"VIEW" }`
* **Expected:** `400 Bad Request` with message: `resource or permission name required`.

---

## 7) Users

**Rule:** Users can have multiple roles; they inherit permissions from those roles.

### Test 7.1 — Success (user with multiple roles sees menus and resources)

* **Setup:** `alice` has roles `ROLE_FIN_MGR` and `ROLE_HR_MGR`.
* **Check menu listing:** `GET /api/menus/app/APP_FIN` with `alice-token`.
* **Expected:** `200 OK` with menus for both roles.

### Test 7.2 — Forbid (user role removed)

* **Action:** remove role `ROLE_HR_MGR` from alice (`POST /api/users/alice/removeRoles` → `["ROLE_HR_MGR"]`).
* **Check:** `GET /api/menus/app/APP_FIN` with alice token; menus linked only to `ROLE_HR_MGR` should no longer be visible.
* **Expected:** `200 OK` without those menus.

### Test 7.3 — Error (adding role to non-existent user)

* **Endpoint:** `POST /api/users/noone/addRoles`
* **Body:** `["ROLE_FIN_MGR"]`
* **Expected:** `404 Not Found`.

---

## Additional Attribution vs Check test matrix (compact table)

Below is a condensed table of the main endpoints used in attribution and usage tests. Use it as a checklist to ensure you cover them all in the Postman collection.

| Module       | Attribution endpoints (manage)                                                                                                      | Usage / Check endpoints                                                                                                                             |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Departments  | `POST /api/departments`, `PUT /api/departments/{id}`                                                                                | `GET /api/departments/:code/applications/available`, `GET /api/departments/:code/menus/available`, `GET /api/departments/:code/resources/available` |
| Applications | `POST /api/applications`, `POST /api/applications/:code/departments`, `POST /api/applications/:code/roles`                          | `GET /api/applications`, `GET /api/applications/:id/menus/available`                                                                                |
| Menus        | `POST /api/menus`, `POST /api/menus/:code/addRoles`, `POST /api/menus/:code/addDepartments`, `POST /api/menus/:code/addPermissions` | `GET /api/menus/app/:appCode`, `GET /api/menus`                                                                                                     |
| Resources    | `POST /api/resources`, `POST /api/resources/:name/add-items`, `POST /api/resources/:code/departments`                               | `GET /api/resources/:name`, `GET /api/resource-items/:id`                                                                                           |
| Roles        | `POST /api/roles`, `POST /api/roles/{name}/addPermissions`, `POST /api/roles/{name}/addUsers`                                       | `GET /api/roles`, `GET /api/roles/{name}/permissions/available`                                                                                     |
| Permissions  | `POST /api/permissions`                                                                                                             | `GET /api/permissions`, `POST /api/authorize/check`, `POST /api/authorize/batch-check`                                                              |
| Users        | `POST /api/users`, `POST /api/users/{username}/addRoles`                                                                            | `GET /api/users/{username}`                                                                                                                         |

(These endpoint names come from the Postman collection and API docs in your artifacts. Use them in tests.  )

---

## Representative use-case (complex) — end-to-end success and fail flows

**Scenario:** Finance department wants Payroll child to manage salary list. We will:

1. Ensure `DEPT_FINANCE` has `APP_FIN`.
2. Create `DEPT_PAYROLL` as child.
3. Create `ROLE_HR_MGR` in `DEPT_PAYROLL`.
4. Create `MENU_SALARY` and link it to `ROLE_HR_MGR`.
5. Create resource `RES_FIN_API` and item `RI_SALARY_LIST` with permission `PERM_VIEW_SALARY`.
6. Grant `ROLE_HR_MGR` the `PERM_VIEW_SALARY`.
7. Create `alice` and assign `ROLE_HR_MGR`.
8. Check `GET /api/menus/app/APP_FIN` as `alice` — should see `MENU_SALARY`.
9. Check `POST /api/authorize/check` for `RI_SALARY_LIST` as `alice` — allowed.

**Fail condition tests in same scenario:**

* If the `ROLE_HR_MGR` does not have `PERM_VIEW_SALARY` or the menu is not linked to `ROLE_HR_MGR`, `alice` should not see the menu or be authorized for the resource.
* If `DEPT_FINANCE` does not have `APP_FIN` (or never got it shared), then attribute attempts for `DEPT_PAYROLL` to add that existing app should fail.

---

## cURL script — full end-to-end (runable examples)

> Replace `{{BASE_URL}}` and `{{TOKEN}}`. Use curl on a shell. Where a created entity ID is required, fetch it with the corresponding `GET` endpoint and use that value.

### Set variables (example)

```bash
BASE_URL="http://localhost:8080"
ADMIN_TOKEN="eyJ..."   # admin token for attribution calls
ALICE_TOKEN="eyJ..."   # user token with ROLE_HR_MGR etc.
```

### 1) Create parent department (Finance)

```bash
curl -s -X POST "$BASE_URL/api/departments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code":"DEPT_FINANCE",
    "name":"Finance",
    "description":"Finance dept",
    "status":"ACTIVE"
  }'
```

### 2) Create child department (Payroll) with parent code obtained from previous call

(assume parent code is DEPT_FINANCE — adjust as needed)

```bash
curl -s -X POST "$BASE_URL/api/departments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code":"DEPT_PAYROLL",
    "name":"Payroll",
    "description":"Payroll",
    "parent_code": "DEPT_FINANCE",
    "status":"ACTIVE"
  }'
```

### 3) Create Application APP_FIN

```bash
curl -s -X POST "$BASE_URL/api/applications" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code":"APP_FIN",
    "name":"Finance App",
    "description":"Finance",
    "status":"ACTIVE",
    "type":"SYSTEM"
  }'
```

### 4) Add APP_FIN to DEPT_FINANCE (attribution)

```bash
curl -s -X POST "$BASE_URL/api/applications/APP_FIN/departments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "codes": ["DEPT_FINANCE"] }'
```

### 5) Create role ROLE_HR_MGR in DEPT_PAYROLL

```bash
curl -s -X POST "$BASE_URL/api/roles" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"ROLE_HR_MGR",
    "description":"HR Manager",
    "department":"DEPT_PAYROLL",
    "status":"ACTIVE"
  }'
```

### 6) Create permission PERM_VIEW_SALARY

```bash
curl -s -X POST "$BASE_URL/api/permissions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"PERM_VIEW_SALARY",
    "description":"View salary",
    "application":"APP_FIN",
    "status":"ACTIVE"
  }'
```

### 7) Create resource and item (RES_FIN_API + RI_SALARY_LIST)

```bash
# create resource
curl -s -X POST "$BASE_URL/api/resources" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"RES_FIN_API",
    "type":"API",
    "description":"Finance API",
    "applicationCode":"APP_FIN",
    "status":"ACTIVE"
  }'

# add resource item — adapt endpoint path if different in your server
curl -s -X POST "$BASE_URL/api/resources/RES_FIN_API/items" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "name":"RI_SALARY_LIST",
      "description":"Salary list item",
      "permission":"PERM_VIEW_SALARY",
      "url":"/api/finance/salary-list"
    }
  ]'
```

### 8) Assign permission to ROLE_HR_MGR (so role has the permission)

```bash
curl -s -X POST "$BASE_URL/api/roles/ROLE_HR_MGR/addPermissions" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["PERM_VIEW_SALARY"]'
```

### 9) Link MENU_SALARY to APP_FIN and to role (create menu, attribute to dept, add role)

```bash
# create menu
curl -s -X POST "$BASE_URL/api/menus" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name":"Salaries",
    "code":"MENU_SALARY",
    "applicationCode":"APP_FIN",
    "type":"SYSTEM_PAGE",
    "position":1,
    "status":"ACTIVE"
  }'

# add department to menu (to share)
curl -s -X POST "$BASE_URL/api/menus/MENU_SALARY/addDepartments" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["DEPT_PAYROLL"]'

# add role to menu
curl -s -X POST "$BASE_URL/api/menus/MENU_SALARY/addRoles" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["ROLE_HR_MGR"]'
```

### 10) Create user alice, assign ROLE_HR_MGR

```bash
curl -s -X POST "$BASE_URL/api/users" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "name": "Alice",
    "email": "alice@example.com"
  }'

curl -s -X POST "$BASE_URL/api/users/alice/addRoles" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '["ROLE_HR_MGR"]'
```

### 11) Usage checks — menus (RBAC)

```bash
curl -s -X GET "$BASE_URL/api/menus/app/APP_FIN" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Accept: application/json"
```

* **Expect**: `MENU_SALARY` included.

### 12) Authorization check — ABAC/RBAC

```bash
curl -s -X POST "$BASE_URL/api/authorize/check" \
  -H "Authorization: Bearer $ALICE_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "resource":"RI_SALARY_LIST",
    "action":"VIEW"
  }'
```

* **Expect**: `200 OK` JSON: `{ "allowed": true, "viaRoles":["ROLE_HR_MGR"], "reason": null }`

### 13) Negative test — user lacking role

* Use `carol` token without roles:

```bash
curl -s -X POST "$BASE_URL/api/authorize/check" \
  -H "Authorization: Bearer $CAROL_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "resource":"RI_SALARY_LIST",
    "action":"VIEW"
  }'
```

* **Expect**: `allowed: false`.

---

## How to validate and what to assert in an automated test runner

* For each `GET` of menus: assert that a menu's code appears / disappears correctly.
* For each `POST /api/authorize/check`: assert `allowed` boolean, and if `true`, assert `viaRoles` contains expected roles.
* For `POST /api/applications/:code/departments` when forbidden: assert `403` and message contains the reason.
* For available endpoints (`/departments/:code/menus/available`, `/departments/:code/applications/available`, `/departments/:code/resources/available`): assert that returned lists only include elements allowed by the parent/child rules (e.g., available apps for child only if parent has them, unless new creation flows apply).
* When testing inheritance (child creates new permission or app), after creation call the relevant `GET` on parent to confirm parent has inherited.

---

## Coverage checklist (run for QA signoff)

* [ ] Departments: parent create, child create, available listing, attribution errors
* [ ] Applications: create, add department, add role, list menus
* [ ] Menus: create, addRoles, addDepartments, accessible via user role
* [ ] Resources: create, add items, link permission, ABAC checks succeed/fail
* [ ] Roles: create, parent-child behavior, adding permissions (allowed and denied scenarios)
* [ ] Permissions: create, attach to resource, inactive permission behavior
* [ ] Users: add roles & remove roles, resulting permission effect validated
* [ ] Authorization endpoints: `/api/authorize/check` and `/api/authorize/batch-check` tested for single and multiple checks
* [ ] Available endpoints for attribution: `/departments/:code/.../available` for apps/menus/resources — ensure these lists follow the parent-child constraints.

---

## Wrap-up notes & tips

* For tests that rely on inheritance (child creates new item and parent inherits), assert both sides: child creation response, and subsequent parent listing to prove inheritance happened.
* When a test expects `403`, confirm the returned JSON includes a friendly message explaining why (useful for debugging).
* Use the Postman collection to group the tests into two folders: **Attribution** (admin token) and **Usage** (user tokens). That makes QA and signoff clear.
* Remember to reset or isolate test data between runs to avoid contamination.