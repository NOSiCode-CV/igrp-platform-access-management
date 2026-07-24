# Validation — Department-Scoped Management

Per-phase acceptance criteria. Passes on JDK 26.0.1 build; assumes local API + Postgres.

Companion documents: [`plan.md`](./plan.md), [`requirements.md`](./requirements.md), [`test-plan.md`](./test-plan.md).

---

## Phase 1 — Scope service + helper endpoint

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=DepartmentScopeServiceTest,DepartmentScopeCteIT` — all green.

### Behaviour

- [ ] Seed: root dept `A` → child `B` → grandchild `C`; role `A.admin` in dept `A`, role `B.mgr` in dept `B`. User `alice` has role `B.mgr` only.
- [ ] `scopeService.scopeOf(alice)` returns `{ B.id, C.id }`. Excludes `A.id` (parent, out of subtree).
- [ ] `scopeService.scopeOf(superadmin)` returns the unbounded marker; `isInScope(anyId)` returns true.
- [ ] `scopeService.scopeOf(userWithNoRoles)` returns empty set.
- [ ] `GET /api/departments/manageable` as `alice` → response contains `B` and `C`, does not contain `A`. Response is a `List<DepartmentDTO>`.
- [ ] Same endpoint as superadmin → contains every non-deleted department.
- [ ] Same endpoint without `igrp.departments.view` → 403.

### Performance

- [ ] Seed 10k departments in a 5-level tree (via SQL script). `scopeOf(user_with_one_role)` completes in < 50ms measured by `System.nanoTime()` in the integration test.
- [ ] Manual: run `EXPLAIN ANALYZE` on the recursive CTE with the seeded tree — verify `Index Scan using idx_department_parent_id`, no seq scan.

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

## Phase 3 — SDK + observability

### Java SDK

- [ ] `client 0.2.0-beta.<next>` builds. Contains `DepartmentsApi.getManageable()` and `error.OutOfScopeException`.
- [ ] Standalone smoke: instantiate `DepartmentsApi`, call `getManageable()` against local API, assert non-empty response.
- [ ] Trigger an out-of-scope operation → SDK throws `OutOfScopeException` with the target dept ID accessible via `e.getDepartmentId()`.

### TypeScript SDK

- [ ] `pnpm build` — success.
- [ ] `DepartmentsClient.getManageable()` returns typed `Department[]`.
- [ ] Error discriminated union: `catch (e) { if (e.code === 'OUT_OF_SCOPE') ... }` compiles and works at runtime.

### Observability

- [ ] `curl -sS http://localhost:8080/igrp-access-management/actuator/prometheus | grep igrp_department_scope_check_denied_total` shows the metric present and > 0 after triggering a denial.
- [ ] Log lines on denial: `grep "scope=OUT_OF_SCOPE" application.log` shows one INFO line per 403, structured with `user`, `target`, `endpoint`.

---

## Cross-phase E2E

Run these after all 3 phases land.

1. **Frontend picker end-to-end**: `alice` logs in via the frontend, opens the department picker, sees only `{ B, C }`; cannot select `A` or `DEPT_IGRP`. Attempts to construct a `PUT /api/departments/A.id` request via DevTools → 403 with `OUT_OF_SCOPE`.
2. **Scope-widening**: alice is granted a role in `A`. Reload the page. The picker now shows `{ A, B, C }`. Her writes to `A` now succeed.
3. **Scope-narrowing**: superadmin revokes alice's role in `B`. Alice's active session — next write attempt within her old scope but outside her new scope → 403 (scope is per-request, R1.3, so it recomputes).
4. **Audit trail**: run 10 out-of-scope attempts. `GET /api/auth/reports/settings?status=ACCESS_DENIED` (as superadmin) shows all 10 rows with the target IDs and attempting user.
5. **Root-creation**: alice attempts `POST /api/departments` with `parentId=null` → 403 `ROOT_DEPARTMENT_FORBIDDEN`, audit row with `operation=CREATE, entityType=DEPARTMENT, status=ACCESS_DENIED`.
