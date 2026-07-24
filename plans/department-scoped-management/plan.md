# Plan — Department-Scoped Management

Companion documents: [`requirements.md`](./requirements.md), [`validation.md`](./validation.md), [`test-plan.md`](./test-plan.md).

Delivered in 3 phases. Depends on the current department/role write endpoints being stable — no schema changes to `t_department` beyond an index.

---

## Phase 1 — `DepartmentScopeService` + read-side helper endpoint

**Goal:** compute a user's management scope on demand; expose it via `GET /api/departments/manageable` for the frontend. No write-side enforcement yet.

### Steps

1. **Index migration** `V13_1__index_department_parent_id.sql`:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_department_parent_id ON t_department (parent_id) WHERE parent_id IS NOT NULL;
   ```
   Cheap; supports the recursive CTE in step 3.

2. **Superadmin detector**: reuse the existing `PermissionCacheService.isSuperAdmin(user)` from the audit feature; no new code needed. Confirm the API works for both JWT and M2M principals.

3. **`DepartmentScopeService`** (new, in `department/application/service/`):
   ```java
   @Service
   @RequestScope
   public class DepartmentScopeService {
       private Set<UUID> cachedScope;  // computed once per request
       private Boolean cachedSuperAdmin;

       public Set<UUID> scopeOf(Authentication auth) { ... }
       public boolean isInScope(UUID departmentId, Authentication auth) { ... }
       public void assertInScope(UUID departmentId, Authentication auth) {
           if (!isInScope(departmentId, auth))
               throw new OutOfScopeException(departmentId);
       }
   }
   ```
   `scopeOf(auth)`:
   - If `isSuperAdmin(auth)` → returns a sentinel `UNBOUNDED` marker (or `null` semantically meaning "all"). `isInScope` short-circuits `true`.
   - Otherwise: SQL recursive CTE from all department IDs owning any of the user's roles:
     ```sql
     WITH RECURSIVE subtree(id) AS (
       SELECT r.department_id FROM t_role r
         JOIN t_user_role ur ON ur.role_id = r.id
         WHERE ur.user_id = :userId AND r.status != 'DELETED'
       UNION
       SELECT d.id FROM t_department d
         JOIN subtree s ON d.parent_id = s.id
         WHERE d.status != 'DELETED'
     )
     SELECT id FROM subtree;
     ```

4. **`OutOfScopeException`** (RuntimeException) with a dedicated `@ExceptionHandler` returning HTTP 403 with body `{ error: "OUT_OF_SCOPE", message: "..." }`.

5. **New endpoint** `GET /api/departments/manageable` in `DepartmentController`:
   - Returns `List<DepartmentDTO>` filtered by the caller's scope.
   - For superadmins, returns everything.
   - Gated by `igrp.departments.view`.

6. **Tests**:
   - `DepartmentScopeServiceTest` — mocked repository; verify (a) superadmin returns unbounded, (b) user with one role → returns closure, (c) user with roles in two unrelated trees → returns union, (d) user with no roles → returns empty set.
   - `DepartmentScopeCteIT` — Testcontainers Postgres; seed a 5-level tree; assert the recursive CTE returns the expected IDs in < 50ms.

### Deliverable

Superadmin sees no change. Non-superadmins can call `GET /api/departments/manageable` and get the frontend picker data.

---

## Phase 2 — Write-side enforcement

**Goal:** every write endpoint in R1.2 calls `scopeService.assertInScope(...)`. Root-department rule enforced.

### Steps

1. **Wire scope check into every command handler** listed in R1.2. Add one line per handler, immediately after argument validation and before the DB write:
   ```java
   // in PostDepartmentCommandHandler
   var parentId = command.getDepartmentDto().getParentId();
   if (parentId == null) {
       scopeService.assertSuperAdmin(auth);  // R1.4
   } else {
       scopeService.assertInScope(parentId, auth);
   }
   ```
2. **Add `assertSuperAdmin(auth)`** to `DepartmentScopeService` — throws `RootDepartmentForbiddenException` (mapped to 403 with `{ error: "ROOT_DEPARTMENT_FORBIDDEN", ... }`).
3. **Role assignment handlers** — locate the assign/unassign role commands (may be in `users/application/commands/` or a `role_assignment` module). Wire the same `assertInScope(role.getDepartmentId(), auth)` check (R1.5, R1.6).
4. **DELETE-cascade safety**: `DeleteDepartmentCommandHandler` must scope-check the target only. Its descendants are in scope by construction. No extra work.
5. **Emit `SettingsAuditEvent` with `status=ACCESS_DENIED` on every failed check** (N4). Requires a listener hook — add publishing to `OutOfScopeException`'s exception handler using the audit feature's `ApplicationEventPublisher`.

### Files touched

| Kind | Count | Notes |
|---|---|---|
| Command handlers modified | ~14 | One `assertInScope` call each; see R1.2 table |
| New classes | 3 | `OutOfScopeException`, `RootDepartmentForbiddenException`, one `@ControllerAdvice` handler |
| Modified classes | 2 | `DepartmentScopeService` (`assertSuperAdmin`), audit event listener wiring |
| Integration tests | 14 | One per gated endpoint — asserts 403 with the right error code for out-of-scope actor |

### Deliverable

All write endpoints in R1.2 gated. Existing endpoints still work for superadmins; new 403 responses for scoped managers acting outside their subtree.

---

## Phase 3 — SDK updates + observability

**Goal:** clients and monitoring reflect the new scoping.

### Steps

1. **Java client SDK** (`modules/client`):
   - Add `DepartmentsApi.getManageable()` returning `List<DepartmentDTO>`.
   - Add typed exception `OutOfScopeException` in the SDK's `error` package that the HTTP layer throws on 403 with `error=OUT_OF_SCOPE`.
   - Bump `client 0.2.0-beta.<next> → 0.2.0-beta.<next+1>`.
2. **TypeScript client**:
   - `DepartmentsClient.getManageable()`.
   - Discriminated union on error responses so `catch (e) { if (e.code === 'OUT_OF_SCOPE') ... }` works.
3. **Metric** `igrp_department_scope_check_denied_total{endpoint, reason}` — Prometheus counter incremented on every `OutOfScopeException` and `RootDepartmentForbiddenException`. Wired to the existing actuator/micrometer setup.
4. **Log line** on every denial: `INFO cv.igrp...scope=OUT_OF_SCOPE user={userId} target={deptId} endpoint={path}` — one structured log line, no PII beyond IDs.

### Deliverable

Frontend can render the scoped picker; SDK consumers get typed errors; ops can alert on scope-denial spikes (signal of misconfiguration or attack).

---

## Effort estimate

| Phase | Wall clock | Risk |
|---|---|---|
| 1 | 2-3h | Low — read-only, well-scoped |
| 2 | 3-4h | Med — 14 handlers to wire; catalog-gap handlers from audit feature complicate mapping |
| 3 | 2h Java + 2h TS + 30m metric | Low — mechanical |

Total: ~8-11h across 2-3 focused sessions.

## Sequencing vs the audit-reports feature

- Phase 1 of this feature can land **in parallel** with Phase 1 or 2 of `unified-audit-and-reports` — no shared files.
- Phase 2 of this feature should land **after** Phase 3 of the audit feature, so `OutOfScopeException`'s 403 handler can publish a `SettingsAuditEvent` through the audit feature's event bus. Otherwise the audit trail for denials is missing.
- Phase 3 can land at any time.
