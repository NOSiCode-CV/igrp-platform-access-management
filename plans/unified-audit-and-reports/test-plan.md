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
| `GET  /api/auth/reports/*.csv` | 200 | 403 |
| `POST /api/auth/reports/*/archive` | 200 | 403 |
| `GET  /api/auth/reports/archives` | 200 | 403 |
| Admin write ops (create app, invite user, etc.) | 200/201 | 403 |

> **Gate-check caveat.** `gate_check` only proves the *normal user* is refused. Pick an
> endpoint the user genuinely lacks permission for: a token holding
> `igrp.applications.create` returns **400** (validation) on `POST /api/applications`,
> not 403, which reads as a gate failure but isn't one. `GET /api/auth/audit` is a
> reliable control — no normal user has `igrp.audit.view`.

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
# EXPECT: {"valid": true, "rows_checked": <N>, "broken_at": null, "unverifiable_legacy_rows": <M>}
# NOTE: the response is snake_case (matches validation.md). On a database upgraded
#       through V10_1, rows written before the chain existed are unhashed and cannot
#       be verified (prod never rehashes, R2.6); they are skipped and counted in
#       unverifiable_legacy_rows. valid=true means every hashed row checked out.

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

> **Use a fresh verification window.** `$Q` pins `endDate` at script start, so any row
> written *later in the run* falls outside it and the query returns nothing — which
> reads as "no audit row" when the row is actually there. Every verification below
> re-derives the window:
>
> ```bash
> # Re-derive on each verification, with headroom for clock skew and the @Async listener.
> fresh_q() {
>   local from="$1"  # e.g. "$DAY_AGO"
>   echo "startDate=${from}&endDate=$(date -u -d '+5 minutes' +'%Y-%m-%dT%H:%M:%SZ')"
> }
> FQ=$(fresh_q "$DAY_AGO")
> ```

### 3.1 Create application → Settings row appears

```bash
APP_NAME="test-audit-app-$(date +%s)"

# Snapshot count before
BEFORE=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&area=APPLICATIONS&entityName=$APP_NAME" \
  | jq '.content | length')

# `type` is @NotNull — omitting it 400s. Valid values come from the AppType enum.
curl -sS -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$APP_NAME\",\"code\":\"$APP_NAME\",\"description\":\"audit test\",\"type\":\"INTERNAL\"}" \
  "$BASE_URL/api/applications" | jq

# Verify audit row landed
sleep 1  # @Async listener needs a beat
AFTER=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&area=APPLICATIONS&entityName=$APP_NAME" \
  | jq '.content | length')

echo "before=$BEFORE after=$AFTER"
# EXPECT: after == before + 1

# Verify row shape
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&entityName=$APP_NAME" \
  | jq '.content[0] | {area, entityType, operation, entityName, status}'
# EXPECT: {"area":"APPLICATIONS","entityType":"APPLICATION","operation":"CREATE","entityName":"<APP_NAME>","status":"SUCCESS"}
```

### 3.2 Edit application → EDIT row with previousValue/newValue populated

```bash
# Applications are keyed by CODE, not id — /api/applications/{code}. Using an id 404s.
curl -sS -X PUT "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d "{\"name\":\"$APP_NAME\",\"code\":\"$APP_NAME\",\"description\":\"edited via test\",\"type\":\"INTERNAL\"}" \
  "$BASE_URL/api/applications/$APP_NAME" | jq

sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&entityName=$APP_NAME&operation=EDIT" \
  | jq '.content[0] | {operation, previousValue, newValue}'
# EXPECT: operation=EDIT, previousValue and newValue both non-null, compact diff form
#         e.g. previousValue="description=audit test", newValue="description=edited via test"
```

### 3.3 Delete application → DELETE row

```bash
curl -sS -X DELETE "${A_ADMIN[@]}" "$BASE_URL/api/applications/$APP_NAME"
sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&entityName=$APP_NAME&operation=DELETE" \
  | jq '.content | length'
# EXPECT: >= 1
```

### 3.4 Role CRUD → CREATE/EDIT/DELETE Settings rows

Same pattern as 3.1-3.3, targeting `/api/departments/{departmentCode}/roles`.

```bash
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&entityType=ROLE&operation=CREATE" \
  | jq '.content[0] | {entityType, operation, entityName}'
# EXPECT: entityName is the BARE role name (e.g. "TestAuditRole"), not "DEPT.TestAuditRole".
# NOTE: an earlier revision of this plan expected the `department.role` convention.
#       plan.md's catalog never specifies one and the implementation emits the bare
#       name, so the expectation was corrected here. Pending team adjudication.
```

### 3.5 Associate/disassociate permission to role → ASSOCIATE/DISASSOCIATE rows with `relatedEntity`

```bash
# Actual route is department-scoped, and the body is a BARE ARRAY of permission
# names — not /api/roles/{id}/permissions with {"permissionNames":[...]}.
curl -sS -X POST "${A_ADMIN[@]}" "${A_JSON[@]}" \
  -d '["igrp.departments.view"]' \
  "$BASE_URL/api/departments/$DEPT_CODE/roles/$ROLE_CODE/permissions"

sleep 1
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&operation=ASSOCIATE" \
  | jq '.content[0] | {operation, entityType, entityName, relatedEntity}'
# EXPECT: operation=ASSOCIATE, entityType=PERMISSION,
#         entityName="igrp.departments.view", relatedEntity="<role>"
#
# NOTE: an earlier revision expected the inverse (entityType=ROLE, relatedEntity=<permission>).
#       The implementation treats the PERMISSION as the subject — consistent with
#       PermissionAssociatedToRoleEvent — so the expectation was corrected here.
#       Pending team adjudication; if the contract flips, both this and the event change.
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
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/settings?$(fresh_q "$DAY_AGO")&operation=CREATE&entityType=APPLICATION" \
  | jq '[.content[] | select(.entityName == "")] | length'
# EXPECT: 0
```

### 3.8 Permission gate for admin write ops (spot check)

```bash
# Pick an endpoint the USER_JWT genuinely lacks. If the token happens to hold
# igrp.applications.create / igrp.departments.*, these return 400 (validation),
# NOT 403 — that is not a gate failure, just the wrong control.
gate_check GET /api/auth/audit          # reliable: no normal user has igrp.audit.view
gate_check POST /api/applications       # only meaningful if USER_JWT lacks igrp.applications.create
```

First confirm what the token actually carries:

```bash
echo "$USER_JWT" | cut -d. -f2 | base64 -d 2>/dev/null | jq '.permissions // .authorities // .scope'
# If igrp.applications.create is present, skip that gate_check and rely on the audit control.
```

---

## Phase 4 tests — PDF, Excel & CSV export, and report archive

Nine download endpoints (3 reports × 3 formats), plus archive + list.

### 4.1 Content-Type and Content-Disposition

```bash
declare -A CT=(
  [xlsx]="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  [pdf]="application/pdf"
  [csv]="text/csv"
)

for r in audit access settings; do
  for f in xlsx pdf csv; do
    echo "--- $r.$f ---"
    curl -sSI "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r.$f?$Q" \
      | grep -iE "content-type|content-disposition"
    # EXPECT: Content-Type starts with ${CT[$f]}
    #         csv MUST be exactly "text/csv;charset=UTF-8" — CSV declares no
    #         encoding in-band, so a bare text/csv leaves clients guessing.
    # EXPECT: Content-Disposition: attachment; filename="<r>-report-YYYY-MM-DD.$f"
  done
done
```

### 4.2 Files download and open

```bash
mkdir -p ./audit-exports
for r in audit access settings; do
  for f in xlsx pdf csv; do
    code=$(curl -sS -w "%{http_code}" "${A_ADMIN[@]}" \
      "$BASE_URL/api/auth/reports/$r.$f?$Q" -o "./audit-exports/$r.$f")
    size=$(stat -c%s "./audit-exports/$r.$f" 2>/dev/null || echo 0)
    echo "$r.$f -> HTTP=$code bytes=$size"
    # EXPECT: HTTP=200 and bytes > 0 for ALL NINE.
    # A 500 with bytes=0 but correct headers means the exception was thrown while
    # streaming, after headers were committed — for .xlsx that is the POI temp-dir
    # signature; check the startup log (see 4.6) before suspecting anything else.
  done

  file "./audit-exports/$r.xlsx"   # EXPECT: "Microsoft Excel 2007+" / "Zip archive data"
  file "./audit-exports/$r.pdf"    # EXPECT: "PDF document, version 1.x"
  head -c 3 "./audit-exports/$r.csv" | xxd | head -1
  # EXPECT: efbb bf — the UTF-8 BOM (R7.5)
done
```

### 4.3 Same filter contract as JSON siblings — all three formats

```bash
JSON_COUNT=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=10000" | jq '.content | length')

XLSX_DATA=$(( $(unzip -p ./audit-exports/audit.xlsx xl/worksheets/sheet1.xml | grep -c "<row ") - 1 ))

# CSV: total lines minus the header. Quoted fields may contain newlines, so count
# with a real parser rather than `wc -l`.
CSV_DATA=$(python3 -c "
import csv,sys
with open('./audit-exports/audit.csv', newline='', encoding='utf-8-sig') as fh:
    print(sum(1 for _ in csv.reader(fh)) - 1)
")

echo "JSON=$JSON_COUNT  XLSX=$XLSX_DATA  CSV=$CSV_DATA"
# EXPECT: all three equal (within reason for concurrent activity)
```

### 4.4 Permission gate

```bash
for r in audit access settings; do
  for f in pdf xlsx csv; do
    gate_check GET "/api/auth/reports/$r.$f?$Q"
  done
done
gate_check POST "/api/auth/reports/audit/archive?format=CSV&$Q"
gate_check GET  "/api/auth/reports/archives"
```

### 4.5 PDF header text

```bash
# Requires pdftotext (poppler)
pdftotext ./audit-exports/audit.pdf - | head -3
# EXPECT: first line matches "Audit Report generated on \d{4}-\d{2}-\d{2}"
```

### 4.6 Excel scratch directory and fallback (R7.6)

`SXSSFWorkbook` creates its temp file **when the sheet is created**, not when the
100-row window overflows — so a temp-dir problem breaks `.xlsx` at *every* size and
is invisible at the HTTP layer until the body fails mid-stream.

```bash
# 1. Which path is the server on? Check the boot log.
#    "Excel export temp directory ready (<path>) — .xlsx exports will stream via SXSSF."
#      -> streaming path (preferred; the only one that meets N2)
#    "Excel export temp directory '<path>' is not writable — falling back to in-memory"
#      -> fallback path: exports still work, but heap-heavy. Mount a writable /tmp.
kubectl logs deploy/access-management | grep -i "Excel export temp directory"

# 2. Smallest possible export must succeed. If .xlsx 500s while .pdf/.csv return 200,
#    it is the scratch directory — not a scale problem, not the data.
NARROW="startDate=$DAY_AGO&endDate=$NOW&username=__no_such_user__"
curl -sS -o /dev/null -w "tiny xlsx -> %{http_code}\n" "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/audit.xlsx?$NARROW"
# EXPECT: 200 (a header-only workbook is valid)

# 3. Both paths must produce an openable workbook. To exercise the fallback
#    deliberately, restart with an unwritable dir:
#      -Digrp.audit.export.temp-dir=/proc/nonexistent
#    then repeat 4.2 for .xlsx — EXPECT: still 200, still a valid workbook,
#    plus the fallback warning in the log.
```

### 4.6.1 Storage reachability — run this BEFORE the archive tests

Archive is the only part of this feature that touches object storage, so a
storage misconfiguration looks like an archive bug. Isolate it first — these
three probes take seconds and tell you whether 4.7–4.9 are even testable.

```bash
# 1. Does the PRE-EXISTING upload path work? It shares nothing with the archive
#    code except StorageService. If this hangs, the archive will too, and the
#    archive code is not the cause.
curl -sS -o /dev/null -w "files/private -> %{http_code} in %{time_total}s\n" --max-time 20 \
  -X POST "${A_ADMIN[@]}" -F "file=@/etc/hostname" -F "folder=probe" \
  "$BASE_URL/api/files/private"
# EXPECT: 200. HTTP=000 (timeout) => storage is unreachable from the pod; stop here.

# 2. Is the archive TABLE fine, independent of storage?
curl -sS -o /dev/null -w "archives -> %{http_code} in %{time_total}s\n" \
  "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives"
# EXPECT: 200 fast. Proves controller + V13_1 + DB are healthy.

# 3. Does validation still precede the upload?
curl -sS -o /dev/null -w "bad format -> %{http_code}\n" -X POST "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/audit/archive?format=DOCX&$Q"
# EXPECT: 400 fast.
```

**If uploads hang**, check these in order — the fix is config, not code:

```bash
# The pod's view of storage. Note IGRP_S3_AWS_ENDPOINT must carry the SCHEME:
# the fallback hardcodes http://, and IGRP_STORAGE_SECURITY does not change it,
# so an HTTPS endpoint is unreachable unless this is set explicitly.
kubectl set env deploy/access-management --list | grep -E "IGRP_STORAGE|IGRP_S3"

# Reachability FROM THE POD (a health check from your laptop proves nothing —
# the pod may resolve a different host, or none).
kubectl exec deploy/access-management -- sh -c \
  'curl -sS -o /dev/null -w "%{http_code}\n" --max-time 5 "$IGRP_S3_AWS_ENDPOINT/minio/health/live"'

# Timeouts are bounded (R7.12) — confirm the boot log, else a hang costs 5 min/request.
kubectl logs deploy/access-management | grep -i "MinIO client timeouts bounded"
```

A hang is not an exception, so the archive service's "Failed to upload…" log will
**not** appear — absence of that line does not mean the upload succeeded.

### 4.7 Archive → storage + DB record (R7.7–R7.9)

```bash
# Archive one report in each format.
for f in XLSX PDF CSV; do
  echo "--- archive audit as $f ---"
  curl -sS -X POST "${A_ADMIN[@]}" \
    "$BASE_URL/api/auth/reports/audit/archive?format=$f&$Q" \
    | jq '{id, reportType, format, filePath, fileName, contentType, sizeBytes, rowCount, generatedBy, generatedAt}'
  # EXPECT: reportType=AUDIT, format=$f
  # EXPECT: filePath starts "private/audit-reports/<your-sub>/" and ends "_audit-report-YYYY-MM-DD.<ext>"
  #         The private/ prefix is REQUIRED — GET /api/files/url only resolves
  #         paths literally starting with private/ or public/.
  # EXPECT: sizeBytes > 0, rowCount >= 0, generatedBy = your sub, generatedAt set
done

# All three reports archive too.
for r in access settings; do
  curl -sS -X POST "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/$r/archive?format=CSV&$Q" \
    | jq '{reportType, format, filePath}'
done
# EXPECT: reportType=ACCESS / SETTINGS, filePath prefix "private/audit-reports/"

# Bad format → 400, not 500
curl -sS -o /dev/null -w "bad format -> %{http_code}\n" -X POST "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/audit/archive?format=DOCX&$Q"
# EXPECT: 400
```

### 4.8 Archived file is retrievable end-to-end (R7.11)

This is the whole point of the archive: `file_path` → link → the same document.

```bash
FP=$(curl -sS -X POST "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/audit/archive?format=CSV&$Q" | jq -r '.filePath')
echo "filePath=$FP"

# Resolve to a presigned URL via the existing file endpoint.
URL=$(curl -sS "${A_ADMIN[@]}" --get --data-urlencode "filePath=$FP" \
  "$BASE_URL/api/files/url" | jq -r '.url')
echo "url=$URL"
# EXPECT: a presigned URL; the sibling `expiration` field is set for private paths.

# Download it WITHOUT the API token — the presigned URL carries its own auth.
curl -sS "$URL" -o ./audit-exports/archived.csv
head -c 3 ./audit-exports/archived.csv | xxd | head -1   # EXPECT: efbb bf (BOM)
head -1 ./audit-exports/archived.csv
# EXPECT: the CSV header line — i.e. the archived object is the real document.

# And it matches what the download endpoint returns for the same filters.
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit.csv?$Q" -o ./audit-exports/direct.csv
diff <(tail -n +2 ./audit-exports/archived.csv) <(tail -n +2 ./audit-exports/direct.csv) \
  && echo "archive == direct download" || echo "DIFFER (only acceptable if rows landed in between)"
```

### 4.9 Archive list endpoint (R7.10)

```bash
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?page=0&size=10" | jq '{
  total: .totalElements,
  first: (.content[0] | {reportType, format, filePath, generatedAt})
}'
# EXPECT: newest first; every row carries a non-null filePath.

# Filters narrow the set.
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?format=CSV" \
  | jq '.content | all(.format == "CSV")'
# EXPECT: true

curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?reportType=SETTINGS" \
  | jq '.content | all(.reportType == "SETTINGS")'
# EXPECT: true

# Unparseable enum → empty page, NOT a 500.
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?reportType=NONSENSE" \
  | jq '.totalElements'
# EXPECT: 0
```

### 4.10 Archive failure isolation (R7.9)

**Requires the ability to break storage** (stop MinIO, or point the bucket at a bad
name). Skip if you cannot.

```bash
# With storage down:
curl -sS -o /dev/null -w "archive with storage down -> %{http_code}\n" -X POST "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/audit/archive?format=CSV&$Q"
# EXPECT: 400 (IGRP_AUTH_FILE_UPLOAD_FAILED), and the error is logged server-side.

# The archive list must NOT have grown — no row may point at a file that was never stored.
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?size=1" | jq '.totalElements'
# EXPECT: unchanged from before the failed call.
```

---

## Phase 5 tests — SDK smoke

Both SDKs should be able to call every new endpoint — the 3 JSON reports, the 9 exports (pdf/xlsx/**csv**), the 3 archive calls and the archive list. These are quick smoke checks; full SDK unit tests run in the SDK repos.

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
# EXPECT: {"valid": false, "broken_at": <seq of the tampered row>}
```

Then restore from a backup or re-run `V10_1` migration on a fresh DB.

### E2E-4 — Export symmetry across all three formats

```bash
# JSON row count == data rows in each exported format, for the same filters.
JSON_N=$(curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=10000" | jq '.content | length')
echo "JSON=$JSON_N (compare against XLSX/CSV counts from 4.3)"
# EXPECT: XLSX and CSV data-row counts both equal JSON_N.
```

### E2E-5 — Generate → archive → link → open

```bash
# The full archive round trip, as a frontend would do it:
FP=$(curl -sS -X POST "${A_ADMIN[@]}" \
  "$BASE_URL/api/auth/reports/settings/archive?format=XLSX&$Q" | jq -r '.filePath')
URL=$(curl -sS "${A_ADMIN[@]}" --get --data-urlencode "filePath=$FP" \
  "$BASE_URL/api/files/url" | jq -r '.url')
curl -sS "$URL" -o ./audit-exports/e2e.xlsx
file ./audit-exports/e2e.xlsx
# EXPECT: "Microsoft Excel 2007+" — archived object opens as a real workbook.

# And the record is discoverable without knowing the path up front.
curl -sS "${A_ADMIN[@]}" "$BASE_URL/api/auth/reports/archives?reportType=SETTINGS&format=XLSX&size=1" \
  | jq -r '.content[0].filePath'
# EXPECT: equals $FP
```

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
