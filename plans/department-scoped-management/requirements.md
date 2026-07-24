# Requirements — Department-Scoped Management

**Feature:** enforce subtree-scoped authorization on department and role write operations for non-superadmin users.
**Target branch:** `version/0.2.0-beta`.
**Interacts with:** [`../unified-audit-and-reports`](../unified-audit-and-reports/) (Settings audit rows record scope-check outcomes; the reports themselves are NOT scoped).

---

## 1. Functional requirements

### 1.1 Scope definition

- **R1.1** A user's **management scope** is the set of departments they may act on. It is computed as:
  ```
  scope(user) = ⋃ { descendants(d) ∪ {d}  |  d ∈ departments_of_users_roles(user) }
  ```
  where `departments_of_users_roles(user)` is the set of departments that own any role the user currently holds (derived from `t_user_role` JOIN `t_role.department_id`), and `descendants(d)` is the transitive closure of `t_department.parent_id` starting at `d`.
- **R1.2** Superadmins (holders of a role in `DEPT_IGRP.superadmin`) have **unbounded scope**. Every scope check short-circuits to allow when the caller is superadmin.
- **R1.3** Scope is computed per-request. It may be cached for the duration of a single HTTP request but not across requests (roles can change mid-session).

### 1.2 Write-side enforcement

Every write operation that mutates a department, a role, or a role's associations must call `DepartmentScopeService.assertInScope(...)` before performing the mutation. If the check fails, the operation returns **HTTP 403** with the error body `{ "error": "OUT_OF_SCOPE", "message": "You cannot manage department <id> — not in your scope." }`.

The endpoints affected:

| Endpoint | Scope check subject |
|---|---|
| `POST   /api/departments` | `parentId` — must be in scope. If `parentId` is null → **superadmin only** (R1.4). |
| `PUT    /api/departments/{id}` | `{id}` — must be in scope. |
| `DELETE /api/departments/{id}` | `{id}` — must be in scope. All descendants are implicitly deleted; they are all in scope by definition (subtree), so no extra checks. |
| `POST   /api/departments/{id}/roles` | `{id}` — must be in scope. |
| `PUT    /api/roles/{roleId}` | Role's owning `department_id` — must be in scope. |
| `DELETE /api/roles/{roleId}` | Role's owning `department_id` — must be in scope. |
| `POST   /api/roles/{roleId}/permissions` | Role's owning `department_id` — must be in scope. |
| `DELETE /api/roles/{roleId}/permissions/{permId}` | Role's owning `department_id` — must be in scope. |
| `POST   /api/departments/{id}/applications` | `{id}` — must be in scope. |
| `DELETE /api/departments/{id}/applications/{appId}` | `{id}` — must be in scope. |
| `POST   /api/departments/{id}/menus` | `{id}` — must be in scope. |
| `DELETE /api/departments/{id}/menus/{menuId}` | `{id}` — must be in scope. |
| `POST   /api/users/{userId}/roles` | Role's owning `department_id` — must be in scope (see R1.5). |
| `DELETE /api/users/{userId}/roles/{roleId}` | Role's owning `department_id` — must be in scope. |

### 1.3 Root department creation is superadmin-only

- **R1.4** A department with `parentId == null` (a root department) may only be created by a superadmin. Non-superadmins hitting `POST /api/departments` with `parentId == null` receive **HTTP 403** with `{ "error": "ROOT_DEPARTMENT_FORBIDDEN", ... }`, even if they hold `igrp.departments.create` and `igrp.departments.manage`.

### 1.4 Role assignment scope

- **R1.5** For `POST /api/users/{userId}/roles`, only the **role's department** must be in the caller's scope. The **target user** has no scope restriction — a scoped manager may assign a role from their subtree to any user platform-wide. (Locked design decision; simpler and matches the confirmed intent.)
- **R1.6** For `DELETE /api/users/{userId}/roles/{roleId}`, same rule — only the role's department must be in scope.
- **R1.7** A scoped manager may not assign a superadmin role (`DEPT_IGRP.superadmin`) to any user, because `DEPT_IGRP` is never in a non-superadmin's scope (only a superadmin has a role in `DEPT_IGRP`).

### 1.5 Permission decoupling

- **R1.8** Scope enforcement is **additive** to existing permission gates. A user still needs `igrp.departments.manage` (or the equivalent granular permission) to attempt the operation. Missing permission → 403 with the current authorization error. Insufficient scope → 403 with `OUT_OF_SCOPE`. Missing both → the permission gate fires first.
- **R1.9** No new permissions are introduced by this feature. It reuses the existing `igrp.departments.*`, `igrp.roles.*`, `igrp.users.roles.*` grants.

### 1.6 Read side — already scoped by existing infrastructure

- **R1.10** `GET /api/departments` is **already scope-filtered** for non-superadmins by the existing `ScopeAspect` + `@Scoped` + `DepartmentSpecificationBuilder.applyScope` chain — the endpoint returns only departments in the caller's visible-department set (their active role's department + all transitive descendants). No new endpoint is needed for this feature; the frontend's department picker reads from `GET /api/departments`.
- **R1.11** Other `GET` endpoints for roles, users, permissions, menus are **not further scoped** in this feature. Everyone with `igrp.<resource>.view` sees everything. Rationale: reads are cheaper to reason about; scoping the rest of the read surface is a separate feature if needed later.
- **R1.12** The Settings Audit Report (`GET /api/auth/reports/settings`) is **not scoped**. Auditors with `igrp.audit.view` see all rows regardless of the audited entity's department. Locked design decision.

## 2. Non-functional requirements

- **N1** Scope-check overhead must add < 10ms p95 to any write endpoint. Achieved via a request-scoped `DepartmentScopeService` bean that computes the closure once per request and caches it.
- **N2** For deep trees (> 20 levels, > 10k departments), the closure query must return in < 50ms. Backed by either a recursive CTE against `t_department (id, parent_id)` with an index on `parent_id`, or a materialized `t_department_closure (ancestor_id, descendant_id, depth)` table maintained by trigger. Decision in `plan.md`.
- **N3** A scope-check failure must not leak information about departments outside the caller's scope. The error message names only the target ID from the request URL, never lists other departments.
- **N4** Every scope-check failure emits a Settings audit event with `status=ACCESS_DENIED` (via the audit-reports feature's `SettingsAuditEventListener`) — supports auditor forensics for privilege-escalation attempts.

## 3. Design decisions (locked)

| Decision | Choice | Reason |
|---|---|---|
| Scope depth | Full subtree (transitive descendants) | User confirmed; matches standard enterprise org-chart authz. |
| Role assignment | Only the role's department must be in scope; target user unrestricted | Simpler; user confirmed the trade-off. |
| User→department association | Derived from `t_user_role JOIN t_role.department_id` | Confirmed existing schema. No new table. |
| Audit reports scoping | Not scoped — `igrp.audit.view` is global | Auditors are cross-cutting. Adds JOIN complexity for no security value. |
| Root department creation | Superadmin only | Explicit user requirement. |
| Closure storage | Recursive CTE first; materialized table only if N2 fails | Simpler; add complexity only if measured slow. |
| New permissions | None | Feature reuses existing gates; scope is an orthogonal check. |
| Response code | 403 `OUT_OF_SCOPE` (distinct from 403 permission-denied) | Distinct error code lets the frontend show a helpful message ("this department is outside your management scope") vs a generic "access denied." |

## 4. Non-goals

- **Scoped read endpoints** for departments/roles/users/permissions. Deferred; explicit follow-up in `roadmap.md`.
- **Multiple non-superadmin scopes per user** (union of scopes from multiple roles in unrelated department trees). The R1.1 formula already handles this naturally — no extra work needed.
- **UI enforcement.** The frontend must call the new `/departments/manageable` endpoint (R1.11) to render only actionable departments, but the backend is authoritative — every write still checks scope server-side.
- **Migration of existing "orphan" data.** If any existing department has `parent_id` pointing to a deleted department, this feature does not repair it. A separate data-cleanup task belongs in `roadmap.md`.
