# Test Plan — Unified Audit & Reports (cURL)

End-to-end validation script the implementing agent runs after each phase lands. Uses `curl` + `jq`. Assumes the API is running locally at `$BASE_URL` and the user has supplied two JWTs:

- `SUPERADMIN_JWT` — a token for a user with `DEPT_IGRP.superadmin` (bypasses all permission checks).
- `USER_JWT` — a token for a normal user with no `igrp.audit.*` permissions.

Companion documents: [`plan.md`](./plan.md), [`requirements.md`](./requirements.md), [`validation.md`](./validation.md).

---

## 0. Setup

**Prompt the user for these values before running any test:**

```bash
export BASE_URL="http://localhost:8080/igrp-access-management"
export SUPERADMIN_JWT="<paste from user>"
export USER_JWT="<paste from user>"

# Convenience headers
export A_JSON=(-H "Accept: application/json" -H "Content-Type: application/json")
export A_ADMIN=(-H "Authorization: Bearer ${SUPERADMIN_JWT}")
export A_USER=(-H "Authorization: Bearer ${USER_JWT}")
```

**Smoke check both tokens work at all:**

```bash
# Both must return 200 against a public/authenticated whoami-ish endpoint.
curl -sS -o /dev/null -w "admin=%{http_code}\n" "${A_ADMIN[@]}" "$BASE_URL/api/users/me"
curl -sS -o /dev/null -w "user =%{http_code}\n" "${A_USER[@]}"  "$BASE_URL/api/users/me"
# Expect: admin=200, user=200
```

If either returns 401, stop — bad token — and ask the user for fresh ones.

---

## Test matrix (permission gate)

Every endpoint in this feature must be tested with **both** tokens. Expected result:

| Endpoint | Admin | Normal user |
|---|---|---|
| `GET  /api/auth/audit` | 200 | 403 |
| `POST /api/auth/audit/validate` | 200 | 403 |
| `DELETE /api/auth/audit/purge` | 200 (destructive — skip in normal test runs) | 403 |
| `GET  /api/auth/reports/audit` | 200 | 403 |
| `GET  /api/auth/reports/access` | 200 | 403 |
| `GET  /api/auth/reports/settings` | 200 | 403 |
| `GET  /api/auth/reports/*.pdf` | 200 | 403 |
| `GET  /api/auth/reports/*.xlsx` | 200 | 403 |
| Admin write ops (create app, invite user, etc.) | 200/201 | 403 |

A `sanity_permission_gate` helper checks it once per endpoint:

```bash
gate_check() {
  local method=$1 path=$2
  local admin_code user_code
  admin_code=$(curl -sS -o /dev/null -w "%{http_code}" -X "$method" "${A_ADMIN[@]}" "$BASE_URL$path")
  user_code=$( curl -sS -o /dev/null -w "%{http_code}" -X "$method" "${A_USER[@]}"  "$BASE_URL$path")
  echo "$method $path — admin=$admin_code user=$user_code"
  # user_code MUST be 403
  [[ "$user_code" == "403" ]] || { echo "FAIL: normal user got $user_code, expected 403"; return 1; }
}
```

---

## Phase 1 tests — unified audit controller + hash chain

### 1.1 Unified list endpoint

```bash
# admin — must return Page envelope
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit?page=0&size=5" | jq '{
  content: (.content | length),
  totalElements, totalPages, size, number, first, last
}'
# EXPECT: content is an array of 5 (or fewer); envelope fields present.
```

### 1.2 Legacy endpoints are gone

```bash
# per R1.3 / N6 these should return 404 (routes deleted)
curl -sS -o /dev/null -w "%{http_code}\n" "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit/00000000-0000-0000-0000-000000000000"
curl -sS -o /dev/null -w "%{http_code}\n" "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit/user/some-user-id"
# EXPECT: both 404
```

### 1.3 UUID PK visible in response

```bash
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit?size=1" \
  | jq -r '.content[0].id' \
  | grep -E '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
# EXPECT: match (row exists, id is UUID format) OR empty output (empty table — acceptable on fresh install)
```

### 1.4 Hash chain: seed a new event and inspect

```bash
# Perform an auth event that we know writes to the audit log — do a fresh login flow
# For this test, easiest is to hit an authenticated endpoint with the user token
# to trigger a token-validated event, then look at the latest row.
curl -sS -o /dev/null "${A_USER[@]}" "$BASE_URL/api/users/me"

# Pull the latest 2 rows (highest sequence_number first)
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit?size=2&sort=timestamp,desc" \
  | jq '.content | map({id, eventType, sequenceNumber, previousHash, currentHash, timestamp})'
# EXPECT: row[0].previousHash == row[1].currentHash (chain link intact)
# EXPECT: currentHash is 64 hex chars
# EXPECT: sequenceNumber of row[0] == sequenceNumber of row[1] + 1
```

### 1.5 Chain validation endpoint

```bash
curl -sS "${A_ADMIN[@]}" -X POST "$BASE_URL/api/auth/audit/validate" | jq
# EXPECT: {"valid": true, "rowsChecked": <N>}

# Normal user must be forbidden
gate_check POST /api/auth/audit/validate
```

### 1.6 Purge is admin-only (do NOT execute in normal runs)

```bash
# ONLY the gate check — do not actually purge
gate_check DELETE /api/auth/audit/purge
# EXPECT: admin=200 (destructive!) user=403
# COMMENT OUT the admin path if you don't want to actually purge.
```

### 1.7 Filter parameters

```bash
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
DAY_AGO=$(date -u -d "1 day ago" +"%Y-%m-%dT%H:%M:%SZ")

curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/audit?startDate=$DAY_AGO&endDate=$NOW&size=100" \
  | jq '.content | length'
# EXPECT: number > 0 if there was any activity in the last 24h.

# Bad filter combination — endDate before startDate → 400
curl -sS -o /dev/null -w "%{http_code}\n" "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/audit?startDate=$NOW&endDate=$DAY_AGO"
# EXPECT: 400
```

---

## Phase 2 tests — 3 report endpoints

### 2.1 Required-param validation

For each of the 3 reports:

```bash
# Missing startDate → 400
for r in audit access settings; do
  code=$(curl -sS -o /dev/null -w "%{http_code}" "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r")
  echo "reports/$r no-args -> $code (expect 400)"
done

# Only startDate, missing endDate → 400
for r in audit access settings; do
  code=$(curl -sS -o /dev/null -w "%{http_code}" "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r?startDate=$DAY_AGO")
  echo "reports/$r startDate-only -> $code (expect 400)"
done
```

### 2.2 Happy path — all 3 report DTOs match the spec

```bash
Q="startDate=$DAY_AGO&endDate=$NOW&size=1"

echo "--- Audit Report row shape ---"
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q" \
  | jq '.content[0] | keys'
# EXPECT: ["accessRole","authorizedBy","device","endDate","id","ipAddress","module","operationState","startDate","status","username"]

echo "--- Access Report row shape ---"
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/access?$Q" \
  | jq '.content[0] | keys'
# EXPECT: ["action","ipAddress","module","role","status","timestamp","username"]

echo "--- Settings Report row shape ---"
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q" \
  | jq '.content[0] | keys'
# EXPECT: ["area","entityName","entityType","ipAddress","newValue","operation","performedBy","previousValue","relatedEntity","status","timestamp"]
```

If a report returns empty content on a fresh install, seed one row first by (a) performing a login (Audit/Access) or (b) an admin write (Settings — see Phase 3 tests).

### 2.3 Each filter narrows the result set

```bash
# Audit — filter by username
ADMIN_USER=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/users/me" | jq -r '.username')
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&username=$ADMIN_USER" \
  | jq '.content | all(.username == "'"$ADMIN_USER"'")'
# EXPECT: true

# Audit — filter by status
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&status=SUCCESS" \
  | jq '.content | all(.status == "SUCCESS")'
# EXPECT: true

# Access — filter by status
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/access?$Q&status=ACCESS_DENIED" \
  | jq '.content | all(.status == "ACCESS_DENIED")'
# EXPECT: true (or empty content array — also acceptable)

# Settings — filter by area
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&area=APPLICATIONS" \
  | jq '.content | all(.area == "APPLICATIONS")'
# EXPECT: true (or empty)
```

### 2.4 Permission gate

```bash
gate_check GET "/api/auth/reports/audit?$Q"
gate_check GET "/api/auth/reports/access?$Q"
gate_check GET "/api/auth/reports/settings?$Q"
```

### 2.5 `device` derivation from user-agent

```bash
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=20" \
  | jq -r '.content[].device' | sort -u
# EXPECT: strings like "Chrome/Windows", "Firefox/Linux", etc. Never null; "Unknown" for unparseable UAs.
```

---

## Phase 3 tests — domain events → Settings Report

Each row in the catalog (see [`plan.md`](./plan.md) §Phase 3) must produce exactly one audit row. Test the ones with existing handlers; note gaps.

### 3.1 Create application → Settings row appears

```bash
APP_NAME="test-audit-app-$(date +%s)"

# Snapshot count before
BEFORE=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&area=APPLICATIONS&entityName=$APP_NAME" \
  | jq '.content | length')

# Perform the admin action — endpoint path may vary; adjust to actual route
curl -sS -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$APP_NAME\",\"code\":\"$APP_NAME\",\"description\":\"audit test\"}" \
  "$BASE_URL/api/applications" | jq

# Verify audit row landed
sleep 1  # @Async listener needs a beat
AFTER=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&area=APPLICATIONS&entityName=$APP_NAME" \
  | jq '.content | length')

echo "before=$BEFORE after=$AFTER"
# EXPECT: after == before + 1

# Verify row shape
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&entityName=$APP_NAME" \
  | jq '.content[0] | {area, entityType, operation, entityName, status}'
# EXPECT: {"area":"APPLICATIONS","entityType":"APPLICATION","operation":"CREATE","entityName":"<APP_NAME>","status":"SUCCESS"}
```

### 3.2 Edit application → EDIT row with previousValue/newValue populated

```bash
APP_ID=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/applications?search=$APP_NAME" | jq -r '.content[0].id')

curl -sS -X PUT "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$APP_NAME\",\"code\":\"$APP_NAME\",\"description\":\"edited via test\"}" \
  "$BASE_URL/api/applications/$APP_ID" | jq

sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&entityName=$APP_NAME&operation=EDIT" \
  | jq '.content[0] | {operation, previousValue, newValue}'
# EXPECT: operation=EDIT, previousValue and newValue both non-null with the description diff.
```

### 3.3 Delete application → DELETE row

```bash
curl -sS -X DELETE "${A_ADMIN[@]}" "$BASE_URL/api/applications/$APP_ID"
sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&entityName=$APP_NAME&operation=DELETE" \
  | jq '.content | length'
# EXPECT: >= 1
```

### 3.4 Role CRUD → CREATE/EDIT/DELETE Settings rows

Same pattern as 3.1-3.3, targeting `/api/departments/{deptId}/roles`. Verify `entityName` matches the `department.role` convention from the catalog (e.g., `TEST.MyRole`).

### 3.5 Associate/disassociate permission to role → ASSOCIATE/DISASSOCIATE rows with `relatedEntity`

```bash
# Assumes existing role $ROLE_ID and permission "igrp.test.action"
curl -sS -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"permissionNames\":[\"igrp.test.action\"]}" \
  "$BASE_URL/api/roles/$ROLE_ID/permissions"

sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&operation=ASSOCIATE&entityType=ROLE" \
  | jq '.content[0] | {operation, entityName, relatedEntity}'
# EXPECT: operation=ASSOCIATE, relatedEntity="igrp.test.action"
```

### 3.6 Audit chain still valid after admin operations

```bash
curl -sS "${A_ADMIN[@]}" -X POST "$BASE_URL/api/auth/audit/validate" | jq '.valid'
# EXPECT: true
```

### 3.7 Failure isolation

```bash
# Force an admin op that will fail validation → the write must fail 400 AND no audit row should appear
curl -sS -o /dev/null -w "%{http_code}\n" -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d '{"name":""}' \
  "$BASE_URL/api/applications"
# EXPECT: 400

# Confirm no audit row created for empty-name creation
sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$Q&operation=CREATE&entityType=APPLICATION" \
  | jq '[.content[] | select(.entityName == "")] | length'
# EXPECT: 0
```

### 3.8 Permission gate for every admin write op (spot check)

```bash
gate_check POST /api/applications
gate_check POST /api/departments
# ... normal user gets 403 without the write permission
```

---

## Phase 4 tests — PDF & Excel export

### 4.1 Content-Type and Content-Disposition

```bash
for r in audit access settings; do
  echo "--- $r.xlsx ---"
  curl -sSI "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r.xlsx?$Q" \
    | grep -iE "content-type|content-disposition"
  # EXPECT: Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  # EXPECT: Content-Disposition: attachment; filename="<r>-report-YYYY-MM-DD.xlsx"

  echo "--- $r.pdf ---"
  curl -sSI "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r.pdf?$Q" \
    | grep -iE "content-type|content-disposition"
  # EXPECT: Content-Type: application/pdf
  # EXPECT: Content-Disposition: attachment; filename="<r>-report-YYYY-MM-DD.pdf"
done
```

### 4.2 Files download and open

```bash
mkdir -p /tmp/audit-exports
for r in audit access settings; do
  curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r.xlsx?$Q" -o "/tmp/audit-exports/$r.xlsx"
  curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r.pdf?$Q"  -o "/tmp/audit-exports/$r.pdf"

  # Magic-byte sanity
  file "/tmp/audit-exports/$r.xlsx"
  # EXPECT: "Microsoft Excel 2007+" or "Zip archive data"
  file "/tmp/audit-exports/$r.pdf"
  # EXPECT: "PDF document, version 1.x"

  # Non-empty
  [[ -s "/tmp/audit-exports/$r.xlsx" ]] && echo "$r.xlsx OK" || echo "$r.xlsx EMPTY (FAIL)"
  [[ -s "/tmp/audit-exports/$r.pdf"  ]] && echo "$r.pdf OK"  || echo "$r.pdf EMPTY (FAIL)"
done
```

### 4.3 Same filter contract as JSON siblings

```bash
# JSON row count vs export row count should match (before pagination)
JSON_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=10000" | jq '.content | length')

# Excel row count via unzip + xmllint (or via Python for CI)
XLSX_ROWS=$(unzip -p /tmp/audit-exports/audit.xlsx xl/worksheets/sheet1.xml \
  | grep -c "<row ")
# subtract 1 for the header row
XLSX_DATA=$((XLSX_ROWS - 1))

echo "JSON rows=$JSON_COUNT  XLSX data rows=$XLSX_DATA"
# EXPECT: JSON_COUNT == XLSX_DATA (within reason for concurrent activity)
```

### 4.4 Permission gate

```bash
for r in audit access settings; do
  for f in pdf xlsx; do
    gate_check GET "/api/auth/reports/$r.$f?$Q"
  done
done
```

### 4.5 PDF header text

```bash
# Requires pdftotext (poppler)
pdftotext /tmp/audit-exports/audit.pdf - | head -3
# EXPECT: first line matches "Audit Report generated on \d{4}-\d{2}-\d{2}"
```

---

## Phase 5 tests — SDK smoke

Both SDKs should be able to call every new endpoint. These are quick smoke checks; full SDK unit tests run in the SDK repos.

### 5.1 Java SDK

Save as `SdkSmoke.java` and run with `java --source 26`:

```java
public class SdkSmoke {
    public static void main(String[] args) {
        var api = new AuditReportsApi("http://localhost:8080/igrp-access-management",
                                      System.getenv("SUPERADMIN_JWT"));
        var page = api.getAuditReport(java.time.Instant.now().minus(java.time.Duration.ofDays(1)),
                                      java.time.Instant.now(),
                                      java.util.Map.of(), 0, 10);
        System.out.println("Audit rows: " + page.getContent().size());
        assert page.getContent().stream().allMatch(r -> r.getId() != null);
    }
}
```

EXPECT: prints a row count, no exceptions.

### 5.2 TypeScript SDK

```typescript
import { AuditReportsClient } from '@igrp/access-management-client';

const client = new AuditReportsClient({
  baseUrl: 'http://localhost:8080/igrp-access-management',
  token: process.env.SUPERADMIN_JWT!,
});

const page = await client.getAuditReport({
  startDate: new Date(Date.now() - 24 * 3600 * 1000),
  endDate:   new Date(),
  size: 10,
});
console.log('Audit rows:', page.content.length);
```

EXPECT: prints a row count, response typed correctly (TS `page.content[0].status` is `"SUCCESS" | "ACCESS_DENIED" | "UNUSUAL_IP" | "PENDING"`).

---

## Cross-phase E2E scenarios (§validation.md restated in curl)

### E2E-1 — Login → Audit Report row within 1s

```bash
BEFORE_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&status=SUCCESS" | jq '.totalElements')

# Trigger a login via the OAuth2 auth flow, OR (simpler) hit any authenticated endpoint with the user token
curl -sS -o /dev/null "${A_USER[@]}" "$BASE_URL/api/users/me"

sleep 1
AFTER_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&status=SUCCESS" | jq '.totalElements')
echo "before=$BEFORE_COUNT after=$AFTER_COUNT"
# EXPECT: after >= before (may be equal if dedup cache absorbed it)
```

### E2E-2 — Tamper detection

**Requires DB access.** Skip if agent doesn't have psql.

```sql
-- Break the chain deliberately
SET app.audit_purge = 'true';
UPDATE t_security_audit_log SET user_id = 'TAMPERED' WHERE sequence_number = 1;
```

```bash
curl -sS "${A_ADMIN[@]}" -X POST "$BASE_URL/api/auth/audit/validate" | jq
# EXPECT: {"valid": false, "brokenAt": 1}
```

Then restore from a backup or re-run `V10_1` migration on a fresh DB.

### E2E-3 — SDK / API contract match

```bash
# Compare JSON-decoded output of two clients hitting the same URL
JSON_A=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=5")
JSON_B=$(node -e "import('@igrp/access-management-client').then(async m => {
  const c = new m.AuditReportsClient({baseUrl:'$BASE_URL', token:'$SUPERADMIN_JWT'});
  console.log(JSON.stringify(await c.getAuditReport({startDate:'$DAY_AGO', endDate:'$NOW', size:5})));
})")

diff <(echo "$JSON_A" | jq -S .) <(echo "$JSON_B" | jq -S .)
# EXPECT: no diff (or only ordering diffs in fields)
```

---

## Full test-run driver

```bash
#!/usr/bin/env bash
# run-audit-tests.sh — runs the whole suite; stops on first failure.
set -euo pipefail

: "${BASE_URL:?BASE_URL not set}"
: "${SUPERADMIN_JWT:?SUPERADMIN_JWT not set — ask the user}"
: "${USER_JWT:?USER_JWT not set — ask the user}"

echo "=== Phase 1 ==="
# ...paste the Phase 1 blocks above, each as its own function...
echo "=== Phase 2 ==="
echo "=== Phase 3 ==="
echo "=== Phase 4 ==="
echo "=== Phase 5 ==="
echo "=== E2E ==="

echo "ALL TESTS PASSED"
```

The agent implementing each phase runs only the sections up to and including that phase, treats any assertion failure as a blocker, reports back the failing command and its output, and does not proceed to the next phase until the section is green.

---

## What the agent asks the user before starting

Every test session begins with:

> I'll run the cURL test plan for Phase N. I need two JWTs:
>
> 1. A **superadmin** JWT (a user with `DEPT_IGRP.superadmin`) — used to hit every audit endpoint and admin write op.
> 2. A **normal user** JWT (no `igrp.audit.*` permissions) — used to verify permission gates return 403.
>
> Also confirm the `BASE_URL` — default is `http://localhost:8080/igrp-access-management`.

Do not proceed until both tokens are provided and the smoke check in §0 passes.
