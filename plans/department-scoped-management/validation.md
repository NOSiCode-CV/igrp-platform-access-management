# Validation — Department-Scoped Management

Per-phase acceptance criteria. Passes on JDK 26.0.1 build; assumes local API + Postgres.

Companion documents: [`plan.md`](./plan.md), [`requirements.md`](./requirements.md), [`test-plan.md`](./test-plan.md).

---

## Phase 1 — Extend `ScopeService` with write-side assertions

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=ScopeServiceAssertionsTest` — all green (6/6).

### Behaviour (delegated to existing `ScopeService.getVisibleDepartmentIds`)

- [ ] Seed: root dept `A` → child `B` → grandchild `C`; role `A.admin` in dept `A`, role `B.mgr` in dept `B`. User `alice` has role `B.mgr` only (active role = `B.mgr`).
- [ ] `scopeService.isInScope(B.id)` = true, `isInScope(C.id)` = true, `isInScope(A.id)` = false for alice.
- [ ] `scopeService.assertInScope(A.id)` throws `OutOfScopeException` carrying `departmentId=A.id`.
- [ ] `scopeService.assertSuperAdmin()` throws `RootDepartmentForbiddenException` for alice.
- [ ] Same three calls for the superadmin → all pass (short-circuit).
- [ ] `GET /api/departments` as alice → response contains `B` and `C`, does not contain `A` (existing `@Scoped` + `applyScope` chain). Same as superadmin → contains every non-deleted department. Same as user without `igrp.departments.list` → 403.

### Performance

- [ ] Existing `ScopeService.resolveDescendants` uses `findDirectChildren` (JPQL) recursively. Partial index `idx_department_parent_id` from `V14_1` covers the parent_id lookups. Verify with `EXPLAIN ANALYZE SELECT id FROM t_department WHERE parent_id = ?` shows `Index Scan using idx_department_parent_id`.

---

## Phase 2 — Write-side enforcement

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=*ScopeEnforcementIT` — all green (one integration test per endpoint in R1.2).

### Behaviour — happy path (in scope)

For each endpoint in R1.2, seed a user with a role in a department whose subtree contains the target. Call the endpoint. Assert:

- [ ] Endpoint returns 2xx.
- [ ] The mutation is persisted (row in DB, cascade correct).
- [ ] A Settings audit row lands (via Phase 3 of `unified-audit-and-reports` — this test skips if that phase hasn't landed).

### Behaviour — deny path (out of scope)

For each endpoint in R1.2, seed a user with a role in an unrelated department tree. Call the endpoint. Assert:

- [ ] Endpoint returns **403**.
- [ ] Response body: `{ "error": "OUT_OF_SCOPE", "message": "You cannot manage department <id> — not in your scope." }`
- [ ] No DB mutation happened.
- [ ] A Settings audit row lands with `status=ACCESS_DENIED`, `operation=<attempted>`, `entity_name=<target>` (again, skips gracefully if audit feature Phase 3 not present).

### Root-department rule

- [ ] `POST /api/departments` with `parentId=null` as a scoped manager (even one with `igrp.departments.manage`) → 403 with `{ "error": "ROOT_DEPARTMENT_FORBIDDEN", ... }`.
- [ ] Same call as superadmin → 201.

### Role assignment (R1.5–R1.7)

- [ ] Alice (scope = `{B, C}`) calls `POST /api/users/{bob.id}/roles` with a role whose department is `B` → 200 (role is in scope; target user out of scope is OK per R1.5).
- [ ] Alice calls same endpoint with a role whose department is `A` (parent, out of scope) → 403 `OUT_OF_SCOPE`.
- [ ] Alice tries to assign a role in `DEPT_IGRP.superadmin` → 403 `OUT_OF_SCOPE` (R1.7 — `DEPT_IGRP` never in a non-superadmin's scope).
- [ ] Superadmin assigns any role to any user → 200.

### Permission decoupling (R1.8)

- [ ] Alice without `igrp.departments.manage` calls `PUT /api/departments/{B.id}` (in her scope) → 403 permission-denied (NOT `OUT_OF_SCOPE` — permission gate fires first).
- [ ] Alice with `igrp.departments.manage` calls `PUT /api/departments/{A.id}` (out of scope) → 403 `OUT_OF_SCOPE`.

### Information leak (N3)

- [ ] Every out-of-scope 403 response body must contain exactly the target ID from the URL, NEVER a list of other IDs / department names.

---

## Phase 3 — Observability

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=ScopeServiceAssertionsTest` — still 6/6 green (the `@Autowired(required=false)` metrics field means unit-constructed ScopeService still works without a MeterRegistry).

### Observability

- [ ] Trigger an out-of-scope operation (e.g. Alice tries `PUT /api/departments/{root-code}`).
- [ ] `curl -sS http://localhost:8080/igrp-access-management/actuator/prometheus | grep igrp_department_scope_check_denied_total` shows:
  ```
  igrp_department_scope_check_denied_total{reason="OUT_OF_SCOPE",} 1.0
  ```
- [ ] Trigger a root-department creation attempt as a non-superadmin.
- [ ] Prometheus now shows a second series:
  ```
  igrp_department_scope_check_denied_total{reason="ROOT_DEPARTMENT_FORBIDDEN",} 1.0
  ```
- [ ] Log lines on denial: `grep "scope=OUT_OF_SCOPE" application.log` shows one INFO line per 403, structured with `user` and `target`.

### SDK (dropped — see plan.md Phase 3 note)

No SDK bumps in this phase. Consumers of `GET /api/departments`, department writes, and role writes discriminate the new 403 error codes by reading `ProblemDetail.getProperty("error")` == `"OUT_OF_SCOPE"` or `"ROOT_DEPARTMENT_FORBIDDEN"`. No new SDK method or typed exception required because no new endpoint was added.

---

## Cross-phase E2E

Run these after all 3 phases land.

1. **Frontend picker end-to-end**: `alice` logs in via the frontend, opens the department picker, sees only `{ B, C }`; cannot select `A` or `DEPT_IGRP`. Attempts to construct a `PUT /api/departments/A.id` request via DevTools → 403 with `OUT_OF_SCOPE`.
2. **Scope-widening**: alice is granted a role in `A`. Reload the page. The picker now shows `{ A, B, C }`. Her writes to `A` now succeed.
3. **Scope-narrowing**: superadmin revokes alice's role in `B`. Alice's active session — next write attempt within her old scope but outside her new scope → 403 (scope is per-request, R1.3, so it recomputes).
4. **Audit trail**: run 10 out-of-scope attempts. `GET /api/auth/reports/settings?status=ACCESS_DENIED` (as superadmin) shows all 10 rows with the target IDs and attempting user.
5. **Root-creation**: alice attempts `POST /api/departments` with `parentId=null` → 403 `ROOT_DEPARTMENT_FORBIDDEN`, audit row with `operation=CREATE, entityType=DEPARTMENT, status=ACCESS_DENIED`.
