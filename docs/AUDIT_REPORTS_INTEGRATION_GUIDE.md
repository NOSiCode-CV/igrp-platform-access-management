# iGRP Audit & Reports Integration Guide

**Audience:** two, and you can read just your half.

- **Frontend developers** building an audit/reporting screen against the Access Management API — §1–§6, then §7.
- **Anyone poking the API by hand** with Postman, Insomnia, or cURL — §1–§6, then §8.

**Everything in this guide was verified against a running deployment.** Where the API behaves in a way that will surprise you, §9 says so plainly rather than leaving you to find out.

Companion docs: [`IGRP_PERMISSIONS_INTEGRATION_GUIDE.md`](./IGRP_PERMISSIONS_INTEGRATION_GUIDE.md) for how permissions get granted in the first place.

---

## 1. The big picture

Every security-relevant event in the platform — logins, token issuing, access denials, session changes, and administrative configuration changes — is written to **one** append-only, tamper-evident table. Three *reports* are different views over that single table, and each report can be downloaded in three formats or archived to object storage.

```
┌──────────────────────── one unified audit log (t_security_audit_log) ────────────────────────┐
│                                                                                              │
│   auth events                    admin config changes                                        │
│   LOGIN_SUCCESS, ACCESS_DENIED,  SYSTEM_CONFIGURATION_CHANGED                                │
│   TOKEN_ISSUED, SESSION_*        (create/edit/delete app, role, department, permission…)      │
│         │                                 │                                                  │
│         │  each row is hash-chained to the previous one (tamper evidence)                    │
│         ▾                                 ▾                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
          │                    │                         │
          ▾                    ▾                         ▾
   GET /reports/audit    GET /reports/access      GET /reports/settings
   "who did what,        "who accessed what,      "what configuration
    from where"           allowed or denied"       changed, old → new"
          │                    │                         │
          ├─ .pdf  .xlsx  .csv ─┤ (download, streams all matching rows, no pagination)
          └─ POST …/archive ────┘ (render + store in object storage + DB record)
```

**Which report do I want?**

| You want to show… | Use |
|---|---|
| A general "who did what, from where, on which device" trail | **Audit Report** |
| Access attempts and their outcome (granted / denied / unusual IP) | **Access Report** |
| An admin change log — what setting changed, old value → new value | **Settings Report** |
| The raw log, including hash-chain columns | `GET /api/auth/audit` (§6) |

Audit and Access are views over **authentication/authorization** events. Settings is a view over **administrative configuration** events. They do not overlap — a login never appears in the Settings Report, and creating an application never appears in the Access Report.

---

## 2. Authentication and permissions

Every endpoint in this guide requires a **Bearer JWT**:

```
Authorization: Bearer <token>
```

Two permissions gate the surface:

| Permission | Gates |
|---|---|
| `igrp.audit.view` | All 3 reports, all 9 exports, archive + archive list, and the raw audit log |
| `igrp.audit.purge` | Only `DELETE /api/auth/audit/purge` |

Both are seeded by Flyway and granted to `DEPT_IGRP.Administrator` on install. A user with the `DEPT_IGRP.superadmin` role bypasses permission checks entirely.

**Without `igrp.audit.view` every endpoint returns `403`** — not an empty list. If you get a 403, it's your permissions, not your filters.

---

## 3. The three reports

All three share the same shape:

```
GET /api/auth/reports/{audit|access|settings}
```

- **`startDate` and `endDate` are REQUIRED.** Omitting either returns **400**, not a default range.
- All other filters are optional and **exact-match** (no partial/`LIKE` matching, case-sensitive).
- Paging is standard Spring: `page` (0-based), `size` (default 20), `sort` (e.g. `sort=timestamp,desc`).
- The response is a standard Spring `Page` envelope.

### 3.1 Audit Report — `GET /api/auth/reports/audit`

Filters: `username`, `module`, `accessRole`, `operationState`, `authorizedBy`, `status`

```json
{
  "id": "671ca9d8-df0e-4cf4-a328-98b624c0a426",
  "startDate": null,
  "endDate": null,
  "username": "marcelo.monteiro@nosi.cv",
  "module": "auth",
  "accessRole": "perm-fdl-apps-center",
  "operationState": null,
  "ipAddress": "10.233.118.128",
  "device": "Chrome/Windows",
  "authorizedBy": null,
  "status": "ACCESS_DENIED"
}
```

### 3.2 Access Report — `GET /api/auth/reports/access`

Filters: `username`, `role`, `module`, `action`, `status`

```json
{
  "timestamp": "2026-07-16T11:35:10.979467Z",
  "username": "marcelo.monteiro@nosi.cv",
  "role": "perm-fdl-apps-center",
  "module": "auth",
  "action": "Access Denied",
  "ipAddress": "10.233.118.128",
  "status": "ACCESS_DENIED"
}
```

### 3.3 Settings Report — `GET /api/auth/reports/settings`

Filters: `performedBy`, `area`, `entityType`, `operation`, `entityName`

```json
{
  "timestamp": "2026-07-16T11:42:26Z",
  "performedBy": "superadmin@igrp.cv",
  "area": "APPLICATIONS",
  "entityType": "APPLICATION",
  "operation": "EDIT",
  "entityName": "test-audit-app",
  "relatedEntity": null,
  "previousValue": "description=audit test",
  "newValue": "description=edited via test",
  "ipAddress": "10.4.32.73",
  "status": "SUCCESS"
}
```

`previousValue` / `newValue` are populated for `EDIT` and use a compact diff form: `field=old` → `field=new`, semicolon-separated when several fields change. `relatedEntity` is populated for `ASSOCIATE` / `DISASSOCIATE` / `ASSIGN` / `UNASSIGN` — see §9.6 for which side is which.

### 3.4 Enum values

Use these verbatim in filters. **A misspelled value is not an error** — report filters are plain strings, so `status=SUCESS` or `status=success` returns `200` with an empty page, exactly like a valid filter that matched nothing. Typos look like "no data". (The one exception is `format` on the archive endpoints, which *is* typed and returns `400`.)

| Enum | Values |
|---|---|
| `status` (Audit) | `SUCCESS`, `ACCESS_DENIED`, `UNUSUAL_IP`, `PENDING` |
| `status` (Access) | `SUCCESS`, `UNUSUAL_IP`, `ACCESS_DENIED` |
| `status` (Settings) | `SUCCESS`, `ERROR` |
| `area` | `APPLICATIONS`, `USERS`, `ACCESS` |
| `entityType` | `APPLICATION`, `USER`, `DEPARTMENT`, `ROLE`, `PERMISSION`, `MENU` |
| `operation` | `CREATE`, `DELETE`, `EDIT`, `ACTIVATE`, `DEACTIVATE`, `INVITE`, `CANCEL_INVITE`, `RESEND_INVITE`, `ASSOCIATE`, `DISASSOCIATE`, `ASSIGN`, `UNASSIGN` |

(The backing `AuditStatus` enum also carries `ERROR`; it appears on Settings rows only.)

---

## 4. Exports — PDF, Excel, CSV

Nine endpoints: each report × each format.

```
GET /api/auth/reports/{audit|access|settings}.{pdf|xlsx|csv}
```

They take **exactly the same filters as their JSON sibling**, minus paging: an export streams *every* matching row. `page`/`size` are ignored.

| Format | `Content-Type` | Notes |
|---|---|---|
| `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | Single sheet, bold header row |
| `.pdf` | `application/pdf` | First line: `"<Report> Report generated on YYYY-MM-DD"` |
| `.csv` | `text/csv` | RFC 4180, UTF-8 **with BOM** (so Excel opens it correctly) |

All three send:

```
Content-Disposition: attachment; filename="audit-report-2026-07-16.csv"
```

Row counts match the JSON endpoint exactly — a report returning 42 rows in JSON produces 42 data rows in each of the three files.

A filter matching zero rows is **not** an error: you get a valid, header-only document with `200`.

---

## 5. Archiving a report

Downloads are ephemeral. Archiving **renders the document, stores it in object storage, and records it in the database** — for retention, scheduled reporting, or sharing a link.

```
POST /api/auth/reports/{audit|access|settings}/archive?format={PDF|XLSX|CSV}&startDate=…&endDate=…
```

`format` is required; the report filters are the same as the JSON sibling. An unsupported format (e.g. `DOCX`) returns **400** before anything is rendered or uploaded.

Response (`AuditReportFileDTO`):

```json
{
  "id": "…",
  "reportType": "AUDIT",
  "format": "CSV",
  "filePath": "private/audit-reports/<user-sub>/<uuid>_audit-report-2026-07-16.csv",
  "fileName": "audit-report-2026-07-16.csv",
  "contentType": "text/csv",
  "sizeBytes": 7052,
  "rowCount": 42,
  "filters": "…",
  "periodStart": "2026-07-15T11:00:00Z",
  "periodEnd": "2026-07-16T11:00:00Z",
  "generatedBy": "<user-sub>",
  "generatedAt": "2026-07-16T11:45:00Z"
}
```

### 5.1 Listing archives

```
GET /api/auth/reports/archives?reportType=AUDIT&format=CSV&generatedBy=<sub>&page=0&size=20
```

All filters optional; returns a `Page<AuditReportFileDTO>`.

### 5.2 Turning `filePath` into a downloadable link

`filePath` is a storage key, **not** a URL. Resolve it through the existing file endpoint:

```
GET /api/files/url?filePath=private/audit-reports/<sub>/<uuid>_audit-report-2026-07-16.csv
```

```json
{ "url": "https://minio…/…?X-Amz-Signature=…", "expiration": "2026-07-16T13:01:25" }
```

The returned URL is **presigned and carries its own auth** — fetch it directly, without your API Bearer token. It expires (5 minutes by default), so resolve it on demand rather than caching it.

> **The `private/` prefix is load-bearing.** `GET /api/files/url` only resolves paths that literally start with `private/` or `public/`. Archived reports are always written under `private/`. Don't strip or rewrite the prefix.

---

## 6. The raw audit log (advanced)

Most screens want the reports. Use these when you need the hash-chain columns themselves.

| Endpoint | Purpose |
|---|---|
| `GET /api/auth/audit` | Paginated raw log. Filters: `userId`, `username`, `eventType`, `category`, `ipAddress`, `startDate`, `endDate`. **Dates are optional here** (unlike the reports). |
| `POST /api/auth/audit/validate` | Verify the tamper-evident hash chain |
| `DELETE /api/auth/audit/purge` | **Destructive.** Deletes every row. Requires `igrp.audit.purge`. |

Each row carries `sequenceNumber`, `previousHash`, `currentHash` — each row's `previousHash` equals the prior row's `currentHash`, so any post-hoc edit breaks the chain.

`validate` returns **snake_case** (it predates the camelCase reports):

```json
{ "valid": true, "rows_checked": 28, "broken_at": null, "unverifiable_legacy_rows": 0 }
```

- `valid: false` + `broken_at: <n>` — the chain is broken at that sequence number.
- `unverifiable_legacy_rows` — rows written *before* the hash chain existed. They carry no hashes and are skipped, not treated as tampering. On a database upgraded from an older version this is normally non-zero and is **not** a problem. Production never rehashes, so this count stays.

> `DELETE /api/auth/audit/purge` destroys the entire audit trail. It exists for dev/test resets. Don't wire it to a UI button.

---

## 7. Frontend integration (TypeScript SDK)

Use the published client rather than hand-rolling fetch calls — the DTOs and enums are typed.

```bash
pnpm add @igrp/platform-access-management-client-ts
```

> The package is `@igrp/platform-access-management-client-ts`. (Some older docs call it `@igrp/access-management-client` — that name does not exist.)

### 7.1 Constructing the client

Auth goes in **`headers`**, not a `token` field:

```ts
import { AuditReportsClient } from '@igrp/platform-access-management-client-ts';

const client = new AuditReportsClient({
  baseUrl: 'https://api-demoigrp.nosi.cv/igrp-access-management',
  headers: { Authorization: `Bearer ${accessToken}` },
  // timeout?: number
});
```

### 7.2 Reading a report

`startDate` / `endDate` are **ISO strings**, not `Date` objects:

```ts
const filters = {
  startDate: new Date(Date.now() - 24 * 3600 * 1000).toISOString(),
  endDate:   new Date().toISOString(),
  status: 'ACCESS_DENIED',   // typed: AuditStatus | string
  page: 0,
  size: 20,
};

const res  = await client.getAuditReport(filters);
const page = res.data;                    // ApiResponse<PageResponse<AuditReportRowDTO>>

console.log(page.totalElements);
page.content.forEach(r => console.log(r.username, r.status, r.device));
```

Sibling methods: `getAccessReport(filters)`, `getSettingsReport(filters)` — each with its own filter type (`AccessReportFilters`, `SettingsReportFilters`).

### 7.3 Downloading an export

The nine export methods return a `Blob`:

```ts
const res  = await client.getAuditReportCsv(filters);   // …Xlsx / …Pdf / …Csv
const blob = res.data;

const url = URL.createObjectURL(blob);
const a = document.createElement('a');
a.href = url;
a.download = `audit-report-${new Date().toISOString().slice(0, 10)}.csv`;
a.click();
URL.revokeObjectURL(url);
```

Exports stream **all** matching rows — a wide date range can be a large download. Keep the user's filters on the export call, and consider a spinner: a 100k-row Excel is not instant.

### 7.4 Archive and retrieve

```ts
const { data: file } = await client.archiveAuditReport('CSV', filters);

// filePath is a storage key — resolve it to a presigned URL to download.
const { data: link } = await fileClient.getUrl(file.filePath);
window.open(link.url);   // presigned; do NOT attach the Bearer token
```

List previous archives with `client.listArchives({ reportType: 'AUDIT', page: 0, size: 20 })`.

### 7.5 Building the screen — practical notes

- **Gate the menu entry on `igrp.audit.view`.** Otherwise the page loads and every call 403s.
- **Always send a date range.** There is no "all time" — a missing date is a 400. Default to something like last 7 days.
- **Don't build a free-text search box over the filters.** They are exact-match; a partial `username` returns nothing. Prefer pickers/dropdowns fed by the enums in §3.4.
- **Expect nulls.** On authentication rows, `operationState`, `authorizedBy`, `startDate`, and `endDate` are null (§9.5). Render "—", not "undefined".
- `device` is derived at read time and is never null — worst case it is the literal string `"Unknown"`.

---

## 8. Testing by hand (Postman / Insomnia / cURL)

### 8.1 Minimum setup

Create an environment with `baseUrl` and `token`, then set a collection-level header:

```
Authorization: Bearer {{token}}
```

Sanity-check the token before blaming anything else:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $TOKEN" "$BASE_URL/api/users/me"
# 200 = good. 401 = see §9.1. 403 = missing permission.
```

### 8.2 The date format

`startDate` / `endDate` are ISO-8601 **instants, in UTC, with a trailing `Z`**:

```
2026-07-16T11:00:00Z          ✅
2026-07-16 11:00:00           ❌ 400
2026-07-16                    ❌ 400
2026-07-16T11:00:00+01:00     ✅ (offsets are accepted)
```

Remember to URL-encode when pasting into a query string by hand — Postman does this for you.

### 8.3 A working request set

```bash
export BASE_URL="https://api-demoigrp.nosi.cv/igrp-access-management"
export TOKEN="<paste>"
A=(-H "Authorization: Bearer $TOKEN")

# Use a future endDate so rows created seconds ago are inside the window (§9.2).
START=$(date -u -d '1 day ago'   +%Y-%m-%dT%H:%M:%SZ)
END=$(date   -u -d '+5 minutes'  +%Y-%m-%dT%H:%M:%SZ)
Q="startDate=$START&endDate=$END"

# JSON reports
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/audit?$Q&size=5"    | jq
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/access?$Q&size=5"   | jq
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/settings?$Q&size=5" | jq

# Filtered
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/audit?$Q&status=ACCESS_DENIED" | jq '.totalElements'

# Exports — -o, or you'll dump a binary into your terminal
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/audit.csv?$Q"  -o audit.csv
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/audit.xlsx?$Q" -o audit.xlsx
curl -sS "${A[@]}" "$BASE_URL/api/auth/reports/audit.pdf?$Q"  -o audit.pdf

# Archive, then resolve to a presigned URL and fetch it WITHOUT the token
FP=$(curl -sS -X POST "${A[@]}" "$BASE_URL/api/auth/reports/audit/archive?format=CSV&$Q" | jq -r '.filePath')
URL=$(curl -sS "${A[@]}" --get --data-urlencode "filePath=$FP" "$BASE_URL/api/files/url" | jq -r '.url')
curl -sS "$URL" -o archived.csv

# Hash chain
curl -sS -X POST "${A[@]}" "$BASE_URL/api/auth/audit/validate" | jq
```

### 8.4 Seeing your own requests in the report

Handy for verifying the pipeline end-to-end: call any gated endpoint with a **token lacking `igrp.audit.view`**. That produces an `ACCESS_DENIED` row within about a second, visible in both the Audit and Access reports.

### 8.5 Downloading in Postman

Use **Send and Download** for `.xlsx` / `.pdf` / `.csv`. A normal *Send* renders bytes into the response pane and it looks like garbage — that's the viewer, not a broken endpoint.

---

## 9. Gotchas

The things most likely to cost you an afternoon.

### 9.1 A `401` usually means the *session* died, not the token

The API enforces a server-side session on top of JWT expiry. A token that is still well within its `exp` will be rejected once its session ends:

```json
{"error":"invalid_token","error_description":"session_expired"}   // idle/absolute session timeout
{"error":"invalid_token","error_description":"session_revoked"}   // logout, or the API restarted/redeployed
```

Sessions are considerably shorter-lived than the tokens themselves, so **don't decode `exp` and conclude the token is fine** — read `error_description`. After any redeploy, every existing token is dead: log in again.

### 9.2 An `endDate` of "now" hides rows you just created

The classic one. If you capture `endDate` once at the start of a script and then create something, the new row's timestamp is *after* your `endDate` and falls outside the window — the row exists but your query can't see it, which looks exactly like "auditing is broken".

Use a slightly future `endDate` (`now + 5 minutes`) when verifying an action you just performed.

### 9.3 An inverted range is a `400`, not an empty page

`startDate > endDate` returns **400**. This is deliberate — it surfaces caller bugs instead of silently returning nothing.

### 9.4 Dates are required on reports, optional on the raw log

`/api/auth/reports/*` → **400** without both dates. `/api/auth/audit` → dates are just optional filters. Same-looking API, different rule.

### 9.5 Some columns are always null on authentication rows

`startDate`, `endDate`, `operationState`, and `authorizedBy` are not populated for login/token/access events. They aren't broken; they have no meaning for those events. Render them as "—".

Conversely, Settings-only fields (`previousValue`, `newValue`, `relatedEntity`) are null unless the operation is an `EDIT` or an association.

### 9.6 `ASSOCIATE` rows: the **permission** is the subject

When a permission is associated to a role, the row reads:

```json
{ "operation": "ASSOCIATE", "entityType": "PERMISSION",
  "entityName": "igrp.departments.view", "relatedEntity": "MyRole" }
```

`entityName` is the **permission** and `relatedEntity` is the **role** — not the other way round. Filtering `entityType=ROLE` to find association events returns nothing. One row is emitted per permission, so associating three permissions produces three rows.

### 9.7 `device` says "Unknown" for Postman and cURL

`device` is derived from the `User-Agent` at read time via a small browser × OS lookup. Non-browser clients don't match, so your hand-made requests all show `"Unknown"`. That's correct behaviour, not a bug. To see real values, send a browser UA:

```bash
curl -H "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" …
# → device: "Chrome/Windows"
```

### 9.8 Filters are exact-match, case-sensitive, and fail silently

`username=marcelo` will not match `marcelo.monteiro@nosi.cv`. `status=success` will not match `SUCCESS`. There is no wildcard.

Worse, a wrong value is indistinguishable from no data: `status=NOT_A_STATUS` returns `200` with an empty page, not `400`. When a filtered query comes back empty, **drop the filter and re-run** before concluding the data isn't there.

### 9.9 `username` is the login name, not the user id

`username` in the reports is a human-readable login (`superadmin@igrp.cv`). The JWT `sub` (a UUID) is a *different* field — filter the raw log by `userId` if you have a UUID, and by `username` on the reports if you have a login.

### 9.10 Archiving depends on object storage being reachable

Archive endpoints render the document, then upload it. If storage is misconfigured or unreachable, the request can hang rather than fail fast. Downloads are unaffected — they never touch storage. So: **downloads work but archive hangs ⇒ suspect storage config, not the report code.**

### 9.11 Excel exports need a writable temp directory

`.xlsx` streams through a scratch file (`igrp.audit.export.temp-dir`, default under `java.io.tmpdir`). If that path isn't writable the service falls back to building the workbook in memory — exports still succeed, but memory use grows with row count. On a read-only container filesystem, mount a writable `/tmp`. The boot log tells you which path is active:

```
Excel export temp directory ready (<path>) — .xlsx exports will stream via SXSSF.
Excel export temp directory '<path>' is not writable — falling back to in-memory
```

---

## 10. Quick reference

```
# Reports (dates REQUIRED)
GET  /api/auth/reports/audit      ?startDate&endDate&username&module&accessRole
                                   &operationState&authorizedBy&status&page&size&sort
GET  /api/auth/reports/access     ?startDate&endDate&username&role&module&action&status&…
GET  /api/auth/reports/settings   ?startDate&endDate&performedBy&area&entityType
                                   &operation&entityName&…

# Exports (same filters, no paging)
GET  /api/auth/reports/{audit|access|settings}.{pdf|xlsx|csv}

# Archive
POST /api/auth/reports/{audit|access|settings}/archive?format={PDF|XLSX|CSV}&…
GET  /api/auth/reports/archives   ?reportType&format&generatedBy&page&size
GET  /api/files/url               ?filePath=private/audit-reports/…   → presigned URL

# Raw log
GET    /api/auth/audit            ?userId&username&eventType&category&ipAddress
                                   &startDate&endDate&page&size&sort   (dates optional)
POST   /api/auth/audit/validate   → {valid, rows_checked, broken_at, unverifiable_legacy_rows}
DELETE /api/auth/audit/purge      → destructive; needs igrp.audit.purge
```

| Status | Meaning |
|---|---|
| `200` | OK |
| `400` | Missing/malformed date, inverted range, or a bad `format` on archive (e.g. `DOCX`) |
| `401` | Token *or session* invalid — read `error_description` (§9.1) |
| `403` | Missing `igrp.audit.view` (or `igrp.audit.purge` for purge) |
