# Cascade-Gap Report — access-management-api

> **Scope of this report.** This report is a follow-up to the three cascade fixes just landed
> (`RemovePermissionsFromDepartmentCommandHandler`, `RemoveMenusFromDepartmentCommandHandler`,
> `RemoveApplicationsFromDepartmentCommandHandler`), which now strip the corresponding grants
> from every role in the department **and every non-deleted descendant department** and fire
> session-invalidation events per touched department.
>
> The report lists every other place with the same "unlink a parent link, forget the child
> grants and/or forget to invalidate sessions" shape, with the concrete impact and a
> recommendation for each. **No code changes are made by this report** — recommendations are
> presented for you to prioritize.

Findings are grouped by blast-radius, most severe first.

---

## Severity legend

| Level | Meaning |
|-------|---------|
| **P0** | Live authorization bypass window. Users keep effective access to something the admin thinks they revoked, until their JWT/session expires naturally. |
| **P1** | Stale data drift. The persistent state is inconsistent, but no live bypass — either because a status filter or a downstream check catches it, or because the drift is inert until re-used. |
| **P2** | Missing observability / correctness on edge cases. Would surface as a bug under unusual conditions. |

---

## P0 — Live authorization bypass windows

### 1. `DeleteDepartmentCommandHandler` — no session invalidation for users of the deleted dept

`department/application/commands/DeleteDepartmentCommandHandler.java`

Soft-deletes the department, all its descendant departments, and every non-deleted role
inside each of them. Publishes only `DepartmentDeletedEvent` (settings-audit). It does
**not** publish `RolePermissionChangedEvent` for any of those roles, and it does **not**
publish `DepartmentScopeChangedEvent(CHANGE_STATUS)` for any of the deleted departments.

**Impact.** Users whose sessions were active in a deleted department keep the department
as their active org, keep their roles' permissions in the JWT, and keep passing permission
checks — until their JWT expires (up to `IGRP_SESSION_ABSOLUTE_TIMEOUT_SECONDS`, currently
8 hours). Delete-department is precisely the operation where "log everyone in that dept out
now" is expected behaviour. The listener plumbing already exists
(`SessionInvalidationEventListener.handleDepartmentScopeChanged` invalidates every session
in a department code).

**Recommendation.** After each department (root and every descendant) is marked DELETED,
publish `DepartmentScopeChangedEvent(deptCode, CHANGE_STATUS, actor)`. Also publish
`RolePermissionChangedEvent(roleCode, deptCode, "ROLE_DELETED", actor)` per soft-deleted
role (mirrors `DeleteRoleCommandHandler`) — this precisely invalidates users assigned to
each role via the role listener even if they belong to a different active dept.

### 2. `RemoveRolesFromMenuCommandHandler` — no scope check, no session invalidation

`app/application/commands/RemoveRolesFromMenuCommandHandler.java`

Removes roles from the owning side of the menu↔role join. Per the survey: no
`@Component`-level scope enforcement, no `RolePermissionChangedEvent`, no
`DepartmentScopeChangedEvent`, no settings-audit event. Not `@Transactional` on the class.

**Impact.** Menu revocation is invisible to the session layer. Users assigned to the
un-linked roles keep the menu entry in their JWT `menus`/`resource_access` claim until the
token refreshes. Also violates R1.2: any caller (even from an unrelated department) can
strip roles from a menu that belongs to another department's application.

**Recommendation.**
- Add `scopeService.assertInScope(role.getDepartment().getId())` per role removed (or per
  distinct department seen across the batch).
- Publish per-role `RolePermissionChangedEvent(roleCode, deptCode, "ROLE_MENUS_CHANGED", actor)`.
- Emit `MenuDisassociatedFromRoleEvent` for the audit trail (already used by the
  department-side handler).

### 3. `DeleteRoleCommandHandler` — child roles get silently deleted with no per-child event

`department/application/commands/DeleteRoleCommandHandler.java`

The primary role emits `RolePermissionChangedEvent(roleCode, deptCode, "ROLE_DELETED")`.
The `deleteChildRoles(role)` recursion, however, walks `role.getChildren()` and soft-deletes
each child role without publishing any event.

**Impact.** If a role has hierarchy children (rare but supported), users assigned to those
child roles keep passing permission checks until their JWT expires. The primary role's
users are invalidated correctly.

**Recommendation.** Inside `deleteChildRoles`, after `child.setStatus(DELETED)` and save,
publish `RolePermissionChangedEvent(child.getCode(), child.getDepartment().getCode(),
"ROLE_DELETED", actor)`. Same pattern as the primary-role branch.

### 4. `RemovePermissionsCommandHandler` (role-level) — same silent-child-role bug

`department/application/commands/RemovePermissionsCommandHandler.java`

Removes a permission from a role. Publishes `RolePermissionChangedEvent` for the primary
role. `removePermissionsForChildren(...)` mutates every child role via `removeIf(name eq)`
and saves — but publishes nothing per child. Flagged in the survey report; noting here for
completeness.

**Impact.** Same as (3) — child-role assignees keep the revoked permission in their JWT
until it refreshes.

**Recommendation.** In the child-recursion, publish per-child `RolePermissionChangedEvent`
with `changeType = "PERMISSIONS_REMOVED"`.

### 5. Add-side has no invariant enforcement — permissions/menus/apps can be re-granted to a role for a department that does not offer them

`department/application/commands/AddPermissionsCommandHandler.java`

For permissions, the only add-side validation is:
- Permission is non-deleted.
- If the role has a **parent role**, filter to permissions the parent role already holds.

There is **no** check that "permission is in `role.getDepartment().getPermissions()` or
any ancestor department's permissions". `AddMenusToRole` / `AddApplicationsToRole`-style
handlers do not exist; menus/apps are only assigned via the department-level handlers,
which for menus/apps do check parent-department availability (but only one level up, not
the full ancestor chain).

**Impact.** Immediately after the cascade above scrubs role grants, an administrator (or
any client of the API) could POST the same permission back to any role in the subtree —
and the department-availability invariant would silently be violated again. The cascade
itself becomes a partial protection.

**Recommendation.** Tighten `AddPermissionsCommandHandler` to reject any permission that
isn't linked to the role's department or one of its ancestors. Same fix should be
retroactively considered for the role-side menu/app add flows if/when those handlers exist
(see (10) below).

---

## P1 — Stale data drift

### 6. `DeleteApplicationCommandHandler` — no role/department cleanup

`app/application/commands/DeleteApplicationCommandHandler.java`

Soft-deletes the application and its menu entries. Does **not**:
- Remove the application from `DepartmentEntity.applications`.
- Remove the application from any `RoleEntity` that still holds it via `t_application_role`.
- Publish `DepartmentScopeChangedEvent(CHANGE_APPLICATIONS)` for any affected department.
- Enforce scope.
- Run inside `@Transactional`.

**Impact.** Departments and roles keep pointing at a soft-deleted app; whether this
matters depends on downstream status filtering. Most read paths filter by
`status <> DELETED`, so the drift is mostly inert — but JWT `resource_access` for users
whose roles held that app will keep listing it until session refresh.

**Recommendation.** After marking the app DELETED, sweep `application.getRoles()` empty
(save the app) and remove the app from each affected `DepartmentEntity.applications`.
Fire `DepartmentScopeChangedEvent(CHANGE_APPLICATIONS, actor)` per affected department.
Add `@Transactional` on the handler.

### 7. `DeleteMenuCommandHandler` — no role/department cleanup

`app/application/commands/DeleteMenuCommandHandler.java`

Soft-deletes the menu (renaming the code with a UUID suffix) and recurses into child
menus. Does **not** touch `menuEntry.getRoles()` or `menuEntry.getDepartments()`, does
not publish any event, does not enforce scope.

**Impact.** Symmetric with (6). Users assigned to roles that hold this menu still see the
menu in their JWT until token refresh; roles/departments retain stale links.

**Recommendation.** After each menu is marked DELETED, clear `menuEntry.getRoles()` and
`menuEntry.getDepartments()` (save). Fire `DepartmentScopeChangedEvent(CHANGE_MENUS)` for
each distinct department that was linked. Add scope enforcement based on the application's
owning department(s).

### 8. `RemoveResourcesFromDepartmentCommandHandler` — no cascade to permissions/roles

`department/application/commands/RemoveResourcesFromDepartmentCommandHandler.java`

Does not touch role-side grants (grep confirms no `getPermissions().remove` /
`getRoles().remove` / `publishRolePermissionChanged` / `publishDepartmentScopeChanged`
calls). Resources define what permissions map to which HTTP routes; removing a resource
from a department leaves permissions still granted to roles in that department pointing at
a resource the department no longer exposes.

**Impact.** Depends on how the resource-permission-route mapping is used at runtime. If
the runtime resolves resources through the role's active dept, the resulting 403 is the
expected user-facing behaviour and no drift occurs. If it resolves through the
permission's own resources set (independent of dept), the mismatch is silent.

**Recommendation.** Determine whether the runtime resource check is department-scoped. If
yes, no cascade needed and this can be dismissed. If no, mirror the permission cascade:
strip the removed resources from every permission linked to any role in the subtree, and
fire `DepartmentScopeChangedEvent(CHANGE_RESOURCES)` per touched department. Also publish
the existing `ResourcePermissionDeletedEvent` per affected permission for parity with the
DeletePermission flow.

### 9. `AddMenusToDepartmentCommandHandler` / `AddApplicationsToDepartmentCommandHandler` — no `@Transactional`

Both handlers do multi-entity writes across parent + child departments and can leave the
DB half-updated on a mid-loop exception (e.g. FK constraint on the third child), while the
first two child departments persist. The Remove-side counterparts are `@Transactional`.

**Impact.** Rare but real: a partial add leaves some departments with the menu/app linked
and others without. The department admin sees a 5xx and retries; the retry may succeed
selectively.

**Recommendation.** Add `@Transactional` to both handlers.

---

## P2 — Missing handlers / correctness gaps

### 10. Missing `RemoveMenusFromRoleCommandHandler` and `RemoveApplicationsFromRoleCommandHandler`

The permission side has both a department-level (`RemovePermissionsFromDepartment`) and a
role-level (`RemovePermissions`) revocation handler. Menus and apps only have the
department-level one. There is **no** way for an admin to say "revoke this menu from just
role R" without deleting R entirely or unlinking the menu from the entire department.

**Impact.** Administrators end up either over-revoking (drop the menu from the dept,
affecting other roles) or under-revoking (leave the role's menu access as-is until the
role is deleted). Product decision — worth flagging.

**Recommendation.** Author both handlers, mirroring `RemovePermissionsCommandHandler`
(load role, mutate the reciprocal owning side — `menuEntry.getRoles().remove(role)` /
`application.getRoles().remove(role)` — fire `RolePermissionChangedEvent` and appropriate
audit events, enforce scope).

### 11. `RemoveRolesFromUserCommandHandler` (not read — verify)

Not opened during this review. If the handler removes a role from a user without publishing
`UserRoleChangedEvent`, users retain their JWT `permissions` / `selectedRole` claims that
reference the revoked role until token refresh.

**Recommendation.** Read the handler and confirm it publishes `UserRoleChangedEvent` per
removed assignment; if not, add it.

### 12. Missing per-role event on the new cascades' descendant depth

The three cascades I just implemented fire one `DepartmentScopeChangedEvent` per touched
department (coarse). The listener translates this to "invalidate every user in that
department". A user in the department whose role never held the removed permission still
gets logged out.

**Impact.** UX-only over-invalidation on an admin-triggered operation. Acceptable for
security-first cascade, but worth revisiting if operators complain about spurious
logouts.

**Recommendation.** If precision becomes desirable: fire `RolePermissionChangedEvent`
per affected role (which we already do for permissions; menu/app cascades would need
either a new `RoleMenuChangedEvent` / `RoleApplicationChangedEvent` or reuse of
`RolePermissionChangedEvent` with a distinct `changeType`). The listener would then
invalidate only users assigned to that (role, department) pair.

---

## Suggested execution order

1. **P0 fixes first** (5 items): (1) `DeleteDepartment` invalidation, (2) `RemoveRolesFromMenu` events + scope, (3) `DeleteRole` child-role events, (4) `RemovePermissions` (role) child-role events, (5) tighten `AddPermissions` invariant.
   These close live-bypass windows and can be batched into one PR with paired tests. Estimated diff: ~200 LOC total.

2. **P1 drift** (4 items): (6) `DeleteApplication`, (7) `DeleteMenu`, (8) `RemoveResources` (pending runtime-check confirmation), (9) `AddMenus/AddApplications @Transactional`.
   Separate PR — mostly additive.

3. **P2 correctness** (3 items): (10) new role-side handlers if product agrees they're needed,
   (11) audit `RemoveRolesFromUser`, (12) per-role precision on the new cascades (only if operators complain).

---

## Notes on the fixes just landed

The three cascade handlers now:

- Walk the department subtree in-memory via `DepartmentEntity.getChildrenids()`, filtering
  out DELETED (mirrors the existing recursion style in
  `RemoveMenusFromDepartmentCommandHandler` and `RemoveApplicationsFromDepartmentCommandHandler`).
- Unlink the permission/menu/app from every subtree department (permission handler adds
  this; menu/app handlers already did it).
- Strip the item from role-side grants for every role in the subtree that held it —
  through `role.getPermissions()` (permissions, owning side), `menuEntry.getRoles()`
  (menus, owning side lives on `MenuEntryEntity`, since `RoleEntity` has no `menus`
  collection at all), and `application.getRoles()` (apps, owning side lives on
  `ApplicationEntity` since `RoleEntity.applications` is `mappedBy`).
- Publish `DepartmentScopeChangedEvent(CHANGE_PERMISSIONS | CHANGE_MENUS | CHANGE_APPLICATIONS)`
  per touched department for session invalidation.
- Additionally, the permission handler fires `RolePermissionChangedEvent(PERMISSIONS_REMOVED)`
  per affected role for precise (role, dept) session invalidation.

Test coverage added in
`RemovePermissionsFromDepartmentCommandHandlerTest.testHandle_CascadesToRolesInDepartmentAndDescendants`.
The menu and application handlers' existing test suites continue to pass unchanged (no
new dependencies were introduced to those classes; the scrub uses only pre-existing
repositories + event publisher, which are already mocked by default `@Mock`).
