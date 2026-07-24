# Test Plan — Department-Scoped Management (cURL)

End-to-end validation script the implementing agent runs after each phase lands. Uses `curl` + `jq`. Assumes the API is running locally at `$BASE_URL`.

## Tokens required

**Three JWTs, prompted from the user at the start of every test session:**

- `SUPERADMIN_JWT` — user with a role in `DEPT_IGRP.superadmin`.
- `SCOPED_MANAGER_JWT` — user with `igrp.departments.manage` + `igrp.roles.manage` + `igrp.users.roles.manage`, whose role belongs to a **non-root** department (call it dept `B`, subtree = `{B, C, D, ...}`).
- `USER_JWT` — normal user with no `igrp.departments.*` permissions.

Also required:
- `ROOT_DEPT_A_ID` — ID of a root department NOT in the scoped manager's subtree.
- `MGR_DEPT_B_ID` — ID of the department that IS the scoped manager's home (top of their subtree).
- `MGR_CHILD_DEPT_C_ID` — ID of a child of `B` (in scope).

The agent asks the user for all of the above before running.

---

## 0. Setup

```bash
export BASE_URL="http://localhost:8080/igrp-access-management"
export SUPERADMIN_JWT="<paste>"
export SCOPED_MANAGER_JWT="<paste>"
export USER_JWT="<paste>"
export ROOT_DEPT_A_ID="<paste>"
export MGR_DEPT_B_ID="<paste>"
export MGR_CHILD_DEPT_C_ID="<paste>"

export A_JSON=(-H "Accept: application/json" -H "Content-Type: application/json")
export A_ADMIN=(-H "Authorization: Bearer ${SUPERADMIN_JWT}")
export A_MGR=(-H "Authorization: Bearer ${SCOPED_MANAGER_JWT}")
export A_USER=(-H "Authorization: Bearer ${USER_JWT}")

# Smoke
curl -sS -o /dev/null -w "admin=%{http_code}\n"  "${A_ADMIN[@]}" "$BASE_URL/api/users/me"
curl -sS -o /dev/null -w "mgr  =%{http_code}\n"  "${A_MGR[@]}"   "$BASE_URL/api/users/me"
curl -sS -o /dev/null -w "user =%{http_code}\n"  "${A_USER[@]}"  "$BASE_URL/api/users/me"
# EXPECT: all 200
```

---

## Phase 1 tests — `DepartmentScopeService` + `GET /departments/manageable`

### 1.1 Manageable endpoint — scoped manager sees their subtree

```bash
curl -sS "${A_MGR[@]}" "$BASE_URL/api/departments/manageable" | jq 'map(.id)'
# EXPECT: array contains MGR_DEPT_B_ID and MGR_CHILD_DEPT_C_ID
# EXPECT: array does NOT contain ROOT_DEPT_A_ID
```

### 1.2 Manageable endpoint — superadmin sees everything

```bash
ADMIN_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/departments/manageable" | jq 'length')
TOTAL_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/departments" | jq '.totalElements')
echo "manageable=$ADMIN_COUNT total=$TOTAL_COUNT"
# EXPECT: ADMIN_COUNT == TOTAL_COUNT
```

### 1.3 Manageable endpoint — normal user without view permission

```bash
curl -sS -o /dev/null -w "%{http_code}\n" "${A_USER[@]}" "$BASE_URL/api/departments/manageable"
# EXPECT: 403
```

### 1.4 Manageable endpoint — user with view permission but no roles → empty

If USER_JWT holds `igrp.departments.view`:

```bash
curl -sS "${A_USER[@]}" "$BASE_URL/api/departments/manageable" | jq 'length'
# EXPECT: 0 (they have view but no role -> empty scope)
```

---

## Phase 2 tests — write-side enforcement

### 2.1 Update dept — in scope → 200

```bash
curl -sS -X PUT "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"B renamed\",\"code\":\"B\",\"description\":\"in-scope edit\"}" \
  -w "\nHTTP=%{http_code}\n" \
  "$BASE_URL/api/departments/$MGR_DEPT_B_ID"
# EXPECT: HTTP=200
```

### 2.2 Update dept — out of scope → 403 OUT_OF_SCOPE

```bash
RESP=$(curl -sS -X PUT "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"A hijacked\",\"code\":\"A\",\"description\":\"...\"}" \
  -w "\nHTTP=%{http_code}" \
  "$BASE_URL/api/departments/$ROOT_DEPT_A_ID")
echo "$RESP"
# EXPECT: HTTP=403
# EXPECT: response body contains {"error":"OUT_OF_SCOPE", "message":"You cannot manage department <ROOT_DEPT_A_ID> ..."}
```

### 2.3 Update dept — as superadmin → 200 (bypass)

```bash
curl -sS -o /dev/null -w "%{http_code}\n" -X PUT "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"A edited by admin\",\"code\":\"A\",\"description\":\"...\"}" \
  "$BASE_URL/api/departments/$ROOT_DEPT_A_ID"
# EXPECT: 200
```

### 2.4 Create child dept — in scope → 201

```bash
NEW_CHILD_NAME="B-child-$(date +%s)"
RESP=$(curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$NEW_CHILD_NAME\",\"code\":\"$NEW_CHILD_NAME\",\"parentId\":\"$MGR_DEPT_B_ID\"}" \
  -w "\nHTTP=%{http_code}" \
  "$BASE_URL/api/departments")
echo "$RESP"
# EXPECT: HTTP=201
# EXPECT: response body has an id
NEW_CHILD_ID=$(echo "$RESP" | sed '/^HTTP/d' | jq -r '.id')
```

### 2.5 Create sibling under root A — out of scope → 403

```bash
curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"A-sibling\",\"code\":\"A-sibling\",\"parentId\":\"$ROOT_DEPT_A_ID\"}" \
  -w "\nHTTP=%{http_code}\n" \
  "$BASE_URL/api/departments" \
  | tail -3
# EXPECT: HTTP=403, error=OUT_OF_SCOPE
```

### 2.6 Create ROOT dept (parentId=null) as scoped manager → 403 ROOT_DEPARTMENT_FORBIDDEN

```bash
RESP=$(curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"name":"Rogue root","code":"rogue","parentId":null}' \
  -w "\nHTTP=%{http_code}" \
  "$BASE_URL/api/departments")
echo "$RESP"
# EXPECT: HTTP=403
# EXPECT: body contains {"error":"ROOT_DEPARTMENT_FORBIDDEN", ...}
```

### 2.7 Create ROOT dept as superadmin → 201

```bash
ROOT_NAME="root-$(date +%s)"
curl -sS -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$ROOT_NAME\",\"code\":\"$ROOT_NAME\",\"parentId\":null}" \
  -w "\nHTTP=%{http_code}\n" \
  "$BASE_URL/api/departments" \
  | tail -3
# EXPECT: HTTP=201
```

### 2.8 Delete dept in scope → 204

```bash
curl -sS -X DELETE "${A_MGR[@]}" -w "%{http_code}\n" \
  "$BASE_URL/api/departments/$NEW_CHILD_ID"
# EXPECT: 204
```

### 2.9 Delete dept out of scope → 403

```bash
curl -sS -X DELETE "${A_MGR[@]}" -w "%{http_code}\n" \
  "$BASE_URL/api/departments/$ROOT_DEPT_A_ID"
# EXPECT: 403 OUT_OF_SCOPE
```

### 2.10 Create role in scope → 201

```bash
ROLE_NAME="test-role-$(date +%s)"
RESP=$(curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"code\":\"$ROLE_NAME\",\"description\":\"test\"}" \
  -w "\nHTTP=%{http_code}" \
  "$BASE_URL/api/departments/$MGR_DEPT_B_ID/roles")
echo "$RESP"
# EXPECT: HTTP=201
NEW_ROLE_ID=$(echo "$RESP" | sed '/^HTTP/d' | jq -r '.id')
```

### 2.11 Create role in out-of-scope dept → 403

```bash
curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"code":"rogue-role","description":"..."}' \
  -w "%{http_code}\n" \
  "$BASE_URL/api/departments/$ROOT_DEPT_A_ID/roles"
# EXPECT: 403 OUT_OF_SCOPE
```

### 2.12 Assign role from scope to any user → 200 (R1.5)

```bash
# Get any user ID via superadmin
TARGET_USER_ID=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/users?size=1" | jq -r '.content[0].id')

curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"roleIds\":[\"$NEW_ROLE_ID\"]}" \
  -w "%{http_code}\n" \
  "$BASE_URL/api/users/$TARGET_USER_ID/roles"
# EXPECT: 200 — target user has no scope restriction, only the role's dept matters
```

### 2.13 Assign out-of-scope role → 403

```bash
# Find a role in ROOT_DEPT_A (out of scope for manager)
OOS_ROLE_ID=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/departments/$ROOT_DEPT_A_ID/roles" | jq -r '.content[0].id')

curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"roleIds\":[\"$OOS_ROLE_ID\"]}" \
  -w "%{http_code}\n" \
  "$BASE_URL/api/users/$TARGET_USER_ID/roles"
# EXPECT: 403 OUT_OF_SCOPE
```

### 2.14 Assign superadmin role → 403 (R1.7)

```bash
SUPERADMIN_ROLE_ID=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/departments" \
  | jq -r '.content[] | select(.code == "DEPT_IGRP") | .id' \
  | xargs -I {} curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/departments/{}/roles" \
  | jq -r '.content[] | select(.code == "superadmin") | .id')

curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d "{\"roleIds\":[\"$SUPERADMIN_ROLE_ID\"]}" \
  -w "%{http_code}\n" \
  "$BASE_URL/api/users/$TARGET_USER_ID/roles"
# EXPECT: 403 OUT_OF_SCOPE (DEPT_IGRP never in scope for non-superadmin)
```

### 2.15 Add permissions to role in scope → 200

```bash
curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"permissionNames":["igrp.test.action"]}' \
  -w "%{http_code}\n" \
  "$BASE_URL/api/roles/$NEW_ROLE_ID/permissions"
# EXPECT: 200
```

### 2.16 Add permissions to role out of scope → 403

```bash
curl -sS -X POST "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"permissionNames":["igrp.test.action"]}' \
  -w "%{http_code}\n" \
  "$BASE_URL/api/roles/$OOS_ROLE_ID/permissions"
# EXPECT: 403 OUT_OF_SCOPE
```

### 2.17 Permission-vs-scope precedence (R1.8)

```bash
# USER_JWT: no igrp.departments.manage AT ALL. Even a hypothetically in-scope target must fail on permission first.
curl -sS -X PUT "${A_USER[@]}" "${A_JSON[@]}" \
  -d '{"name":"attempt"}' \
  -w "%{http_code}\n" \
  "$BASE_URL/api/departments/$MGR_DEPT_B_ID" \
  | tail -3
# EXPECT: 403 (permission-denied, NOT OUT_OF_SCOPE) — permission gate fires first
```

### 2.18 Information leak check (N3)

```bash
BODY=$(curl -sS -X PUT "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"name":"..."}' \
  "$BASE_URL/api/departments/$ROOT_DEPT_A_ID")
# Body MUST contain ROOT_DEPT_A_ID and MUST NOT contain any other department ID or code
echo "$BODY" | jq
# Manual inspection: no list of other departments, no hints about the tree structure
```

---

## Phase 3 tests — SDK + observability

### 3.1 Prometheus counter present

```bash
# Trigger a denial
curl -sS -o /dev/null -X PUT "${A_MGR[@]}" "${A_JSON[@]}" \
  -d '{"name":"..."}' "$BASE_URL/api/departments/$ROOT_DEPT_A_ID"

# Check the metric
curl -sS "$BASE_URL/actuator/prometheus" | grep igrp_department_scope_check_denied_total
# EXPECT: metric line with value >= 1
```

### 3.2 Structured log line

```bash
# Requires log tail access
tail -100 /path/to/application.log | grep "scope=OUT_OF_SCOPE"
# EXPECT: at least one line with user=<mgr>, target=$ROOT_DEPT_A_ID, endpoint=PUT /api/departments/...
```

### 3.3 Java SDK smoke

```java
public class ScopeSmoke {
    public static void main(String[] a) throws Exception {
        var api = new DepartmentsApi(BASE_URL, System.getenv("SCOPED_MANAGER_JWT"));
        var manageable = api.getManageable();
        System.out.println("Manageable count: " + manageable.size());
        try {
            api.update(ROOT_DEPT_A_ID, /* payload */);
            throw new AssertionError("expected OutOfScopeException");
        } catch (OutOfScopeException e) {
            System.out.println("Correctly denied for dept: " + e.getDepartmentId());
        }
    }
}
```

### 3.4 TS SDK smoke

```typescript
const client = new DepartmentsClient({ baseUrl, token: SCOPED_MANAGER_JWT });
const manageable = await client.getManageable();
console.log('Manageable:', manageable.length);

try {
  await client.update(ROOT_DEPT_A_ID, { name: '...' });
  throw new Error('expected OUT_OF_SCOPE');
} catch (e: any) {
  if (e.code !== 'OUT_OF_SCOPE') throw e;
  console.log('Correctly denied');
}
```

---

## Full test-run driver

Same shape as the audit feature's `test-plan.md`. `run-scoped-mgmt-tests.sh` executes §0 → §Phase 1 → §Phase 2 → §Phase 3, stopping on first assertion failure.

---

## What the agent asks the user before starting

> I'll run the cURL test plan for Phase N of the department-scoped management feature. I need three JWTs and three department IDs:
>
> 1. A **superadmin** JWT.
> 2. A **scoped manager** JWT — a user with `igrp.departments.manage` + `igrp.roles.manage` + `igrp.users.roles.manage`, whose role belongs to a non-root department.
> 3. A **normal user** JWT — no `igrp.departments.*` permissions.
>
> Plus:
> - `ROOT_DEPT_A_ID` — a root department NOT in the scoped manager's subtree.
> - `MGR_DEPT_B_ID` — the manager's home department (top of their subtree).
> - `MGR_CHILD_DEPT_C_ID` — a child of B (in scope).
>
> Confirm the `BASE_URL` — default `http://localhost:8080/igrp-access-management`.

Do not proceed until all values are provided and the §0 smoke check passes.
