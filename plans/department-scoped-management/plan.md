# Plan — Department-Scoped Management

Companion documents: [`requirements.md`](./requirements.md), [`validation.md`](./validation.md), [`test-plan.md`](./test-plan.md).

Delivered in 3 phases. Depends on the current department/role write endpoints being stable — no schema changes to `t_department` beyond an index.

---

## Phase 1 — Extend `ScopeService` with write-side assertions

**Goal:** add `isInScope(Integer)`, `assertInScope(Integer)`, `assertSuperAdmin()` to the existing `ScopeService`, plus the two exception types and handlers. Do NOT duplicate the existing scope infrastructure — read-side scoping on `GET /api/departments` is already handled by `ScopeAspect` + `@Scoped` + `DepartmentSpecificationBuilder.applyScope`, which resolves the subtree via `ScopeService.getVisibleDepartmentIds()` → `resolveDescendants` → `findDirectChildren`.

### Steps

1. **Index migration** `V14_1__index_department_parent_id.sql`:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_department_parent_id ON t_department (parent_id) WHERE parent_id IS NOT NULL;
   ```
   Cheap; supports the existing `findDirectChildren` JPQL query used by `ScopeService.resolveDescendants`.

2. **`OutOfScopeException` + `RootDepartmentForbiddenException`** — RuntimeExceptions in `department/domain/exceptions/`. Both mapped by `GlobalExceptionHandler` to `ProblemDetail` with HTTP 403 and error codes `OUT_OF_SCOPE` / `ROOT_DEPARTMENT_FORBIDDEN` so the frontend can distinguish them from generic permission denial.

3. **Extend `ScopeService`** (existing, in `shared/infrastructure/service/`) with:
   ```java
   public boolean isInScope(Integer departmentId) { /* short-circuit for superadmin */ }
   public void assertInScope(Integer departmentId) { throw OutOfScopeException }
   public void assertSuperAdmin() { throw RootDepartmentForbiddenException }
   ```
   All three delegate to the existing `isSuperAdmin()` and `getVisibleDepartmentIds()` machinery — no new query, no new cache, no duplicate scope logic. `assert*` methods log one structured line (`scope=OUT_OF_SCOPE user={id} target={dept}` / `scope=ROOT_DEPARTMENT_FORBIDDEN user={id}`) before throwing — supports the Prometheus counter and log-based forensics in Phase 3.

4. **No new endpoint** — the frontend's department picker calls the existing `GET /api/departments` which already returns the scope-filtered set (verified: `GetDepartmentsQueryHandler` passes `new ScopeContext()`; `@Scoped` populates it; `DepartmentSpecificationBuilder.applyScope` adds `id IN (visibleDepartments)` when not superadmin). Superadmins see the full list; scoped managers see their subtree; users with no relevant roles see `[]`.

5. **Tests** (unit, mocking `ScopeService` collaborators):
   - `ScopeServiceAssertionsTest` — 6 cases: superadmin unbounded; scoped user reads visible set; assertInScope throws with target id; assertSuperAdmin throws for non-admin; null departmentId always false; empty visible set always false.
   - Existing `GET /api/departments` scope behaviour is already covered by existing tests — no re-coverage.

### Deliverable

`ScopeService` gains the write-side API needed by Phase 2. No new endpoint. No new query. No duplicate scope machinery.

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

## Phase 3 — Observability

**Goal:** ops can alert on scope-denial spikes as a signal of misconfiguration or privilege-escalation probing.

**Reduced scope note:** the SDK work originally planned here (`DepartmentsApi.getManageable()` + typed `OutOfScopeException` in the SDK) was dropped because Phase 1 no longer adds a `/manageable` endpoint (the existing `GET /api/departments` is already scope-filtered). SDK consumers discriminate the two new 403 error codes by reading the `error` property on the returned `ProblemDetail` — no new SDK method or typed exception is required.

### Steps

1. **`DepartmentScopeMetrics`** (new, in `department/infrastructure/metrics/`) — Micrometer counter `igrp.department.scope.check.denied` tagged with `reason=OUT_OF_SCOPE|ROOT_DEPARTMENT_FORBIDDEN`. Follows the existing `SessionMetrics` naming convention (`igrp.<domain>.<counter>` → `igrp_<domain>_<counter>_total` in Prometheus).

2. **Wire into `ScopeService`** — `assertInScope` and `assertSuperAdmin` bump the counter on denial, right before throwing. The `DepartmentScopeMetrics` bean is `@Autowired(required = false)` so unit tests that construct `ScopeService` directly (without a `MeterRegistry`) don't need to supply it.

3. **Structured log line** — already added in Phase 1: `scope=OUT_OF_SCOPE user={id} target={dept}` and `scope=ROOT_DEPARTMENT_FORBIDDEN user={id}` at INFO level. No further work.

### Deliverable

Two Prometheus time series available on `/actuator/prometheus`:

```
igrp_department_scope_check_denied_total{reason="OUT_OF_SCOPE"}
igrp_department_scope_check_denied_total{reason="ROOT_DEPARTMENT_FORBIDDEN"}
```

Ops can alert on non-zero rates; auditors get log lines already in place from Phase 1.

---

## Effort estimate

| Phase | Wall clock | Risk |
|---|---|---|
| 1 | 2-3h | Low — read-only, well-scoped |
| 2 | 3-4h | Med — 14 handlers to wire; catalog-gap handlers from audit feature complicate mapping |
| 3 | ~30min (only the metric — SDK work dropped, logs already done) | Low |

Total: ~8-11h across 2-3 focused sessions.

## Sequencing vs the audit-reports feature

- Phase 1 of this feature can land **in parallel** with Phase 1 or 2 of `unified-audit-and-reports` — no shared files.
- Phase 2 of this feature should land **after** Phase 3 of the audit feature, so `OutOfScopeException`'s 403 handler can publish a `SettingsAuditEvent` through the audit feature's event bus. Otherwise the audit trail for denials is missing.
- Phase 3 can land at any time.
