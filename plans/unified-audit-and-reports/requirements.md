# Requirements — Unified Audit & Reports

**Feature:** consolidate audit logging into a single tamper-evident trail and expose 3 report endpoints (Audit, Access, Settings) with export.
**Target branch:** `version/0.2.0-beta`.
**Source spec:** `~/Downloads/audit-reports-requirements.md` (2026-07-14).
**Reference implementation** (audit-only subset to be extracted): `feature/audit-logging-implementation` (MR !362).

---

## 1. Functional requirements

### 1.1 Unified audit log

- **R1.1** There must be **one** audit table and one write-side service. `AuthAuditLog` (and its supporting classes) must be deleted. The consolidated table remains `t_security_audit_log`, and the consolidated domain class remains `SecurityAuditLog`.
- **R1.2** Every audit event — including authentication events (login/logout/token issuing/token invalid), session events, authorization decisions, and administrative configuration changes — must be recorded in the unified log.
- **R1.3** The unified controller is `AuthAuditController` at `/api/auth/audit` (name preserved for SDK/client compatibility).

### 1.2 Tamper evidence

- **R2.1** Each row must carry a cryptographic hash chain (`previous_hash`, `current_hash`, `sequence_number`). `current_hash = HMAC-SHA256(chain_secret, previous_hash || row_serialized_fields)`.
- **R2.2** All fields persisted on the row participate in the hash input.
- **R2.3** Writes must be serialized cluster-wide via a Postgres advisory lock to prevent chain forks under concurrent writers.
- **R2.4** An append-only Postgres trigger must block `UPDATE` and `DELETE` on the table outside a dedicated `PURGE` privilege.
- **R2.5** A validator service must detect broken chains on demand (endpoint or scheduled job — TBD in Phase 1).
- **R2.6** For dev/staging only, a startup flag `igrp.audit.chain.rehash-on-boot=true` rewalks the entire table and rewrites `current_hash` values (used after schema changes that extend the hash input). Production must never rehash.

### 1.3 IP handling

- **R3.1** The audit log stores both `ip_address` (raw, VARCHAR 45) and `ip_hash` (HMAC-SHA256 of the raw IP with `AUDIT_CHAIN_SECRET`).
- **R3.2** The raw IP is used in report responses. The hash participates in the chain input so that editing the raw IP breaks the chain.

### 1.4 Authorization

- **R4.1** A single permission, `igrp.audit.view`, gates the raw audit log endpoints AND all report endpoints AND all export endpoints.
- **R4.2** A separate permission, `igrp.audit.purge`, gates the purge endpoint.
- **R4.3** Both permissions are seeded via Flyway migration and granted to `DEPT_IGRP.Administrator` on installation.

### 1.5 Report endpoints

Three paginated JSON endpoints under `/api/auth/reports`.

#### 1.5.1 Audit Report — `GET /api/auth/reports/audit`

Required query params: `startDate`, `endDate`.
Optional filters: `username`, `module`, `accessRole`, `operationState`, `authorizedBy`, `status`, `page`, `size`, `sort`.
Response is `Page<AuditReportRowDTO>` with fields:

| Field | Type | Origin |
|---|---|---|
| `id` | UUID (string) | existing PK — converted from Long to UUID in Phase 1 |
| `startDate` | Instant | new column `period_start` |
| `endDate` | Instant | new column `period_end` |
| `username` | string | existing `username` |
| `module` | string | new column `application_module` |
| `accessRole` | string | new column `access_role` |
| `operationState` | string | new column `operation_state` |
| `ipAddress` | string | existing raw `ip_address` |
| `device` | string | derived at read time from existing `user_agent` (Chrome/Firefox/Edge/Safari × Windows/macOS/Linux/Android/iOS lookup table; no external lib) |
| `authorizedBy` | string | new column `authorized_by` |
| `status` | enum `SUCCESS | ACCESS_DENIED | UNUSUAL_IP | PENDING` | new column `status` |

#### 1.5.2 Access Report — `GET /api/auth/reports/access`

Required: `startDate`, `endDate`.
Optional: `username`, `role`, `module`, `action`, `status`, `page`, `size`, `sort`.
Response is `Page<AccessReportRowDTO>` with fields:

| Field | Type | Origin |
|---|---|---|
| `timestamp` | Instant | existing `timestamp` |
| `username` | string | existing |
| `role` | string | new column `access_role` (shared with Audit Report) |
| `module` | string | new column `application_module` (shared) |
| `action` | string | new column `action` |
| `ipAddress` | string | existing |
| `status` | enum `SUCCESS | UNUSUAL_IP | ACCESS_DENIED` | new column `status` |

#### 1.5.3 Settings Report — `GET /api/auth/reports/settings`

Required: `startDate`, `endDate`.
Optional: `performedBy`, `area`, `entityType`, `operation`, `entityName`, `page`, `size`, `sort`.
Response is `Page<SettingsReportRowDTO>` with fields:

| Field | Type | Origin |
|---|---|---|
| `timestamp` | Instant | existing |
| `performedBy` | string | existing `username` |
| `area` | enum `APPLICATIONS | USERS | ACCESS` | new column `settings_area` |
| `entityType` | enum `APPLICATION | USER | DEPARTMENT | ROLE | PERMISSION | MENU` | new column `settings_entity_type` |
| `operation` | enum (see §1.6 catalog) | new column `settings_operation` |
| `entityName` | string | new column `entity_name` |
| `relatedEntity` | string, nullable | new column `related_entity` |
| `previousValue` | string, nullable | new column `previous_value` |
| `newValue` | string, nullable | new column `new_value` |
| `ipAddress` | string | existing |
| `status` | enum `SUCCESS | ERROR` | new column `status` |

### 1.6 Settings event catalog (Phase 3 scope)

Every listed platform action must publish a strongly-typed Spring `ApplicationEvent`; a single `SettingsAuditEventListener` catches them all and writes to `SecurityAuditService`. Full mapping is in `plan.md` §Phase 3.

**Symmetry rule:** every action listed as `CREATE` / `ACTIVATE` / `ASSOCIATE` / `ASSIGN` has its inverse (`DELETE` / `DEACTIVATE` / `DISASSOCIATE` / `UNASSIGN`). User invitation uses `INVITE ↔ CANCEL_INVITE`. `RESEND_INVITE` is repeatable with no inverse.

**Catalog gap:** any listed action without a handler today (`activate`/`deactivate`/`invite`/`cancel-invite`/`resend-invite` are candidates) is skipped in Phase 3 with a `TODO(catalog-gap)` comment in the event mapping. Creating those handlers is a separate deferred effort in `roadmap.md`.

### 1.7 Export

Two delivery modes, both over the same three reports and the same three formats:
**download** (stream the document straight back) and **archive** (store the
document and hand back a retrievable path).

#### 1.7.1 Download

- **R7.1** Each of the 3 report endpoints has PDF, Excel and CSV variants — 9 endpoints:
  - `GET /api/auth/reports/audit.pdf`, `.xlsx`, `.csv`
  - `GET /api/auth/reports/access.pdf`, `.xlsx`, `.csv`
  - `GET /api/auth/reports/settings.pdf`, `.xlsx`, `.csv`
- **R7.2** Export endpoints accept the same filter contracts as their JSON counterparts, minus pagination — export streams all rows matching the filter.
- **R7.3** Exports are generated in-request (no async job queue). Rows stream via Apache POI `SXSSFWorkbook` for Excel, OpenPDF direct render for PDF, and a direct writer for CSV. Memory stays flat for reports up to 100k rows on the download path.
- **R7.4** PDF header reads "Audit Report generated on YYYY-MM-DD" (or the corresponding title for the other two reports); localized via `MessageSource`.
- **R7.5** CSV is RFC 4180: `,` separator, `CRLF` line ends, fields containing a separator/quote/line break are quoted with embedded quotes doubled. Written UTF-8 with a BOM so Excel reads accented names correctly on double-click. CSV touches no disk and no workbook model.
- **R7.6** `SXSSFWorkbook` stages rows in a temp file that it creates **eagerly when the sheet is created** — not only once the in-memory window overflows. Excel export therefore requires a writable scratch directory, or it fails at any row count, inside the response body after headers are committed (an opaque 500). Accordingly:
  - the scratch directory is configurable via `igrp.audit.export.temp-dir` (default `${java.io.tmpdir}/igrp-audit-exports`) and is **probed for writability once at startup**, not per request;
  - where it is not writable, Excel export **falls back to in-memory generation** (`XSSFWorkbook`) rather than failing — trading R7.3's flat memory for availability, and logging a warning naming the directory;
  - the choice is made before any byte is written to the response, so a fallback can never leave a half-written document;
  - deployments are still expected to provide a writable directory (see `k8s/deployment.yaml`), because streaming is what upholds N2.

#### 1.7.2 Archive

- **R7.7** Each report can be archived instead of downloaded, in any of the 3 formats:
  - `POST /api/auth/reports/audit/archive?format={PDF|XLSX|CSV}`
  - `POST /api/auth/reports/access/archive?format={PDF|XLSX|CSV}`
  - `POST /api/auth/reports/settings/archive?format={PDF|XLSX|CSV}`

  Each accepts the same filters as its JSON sibling, generates the document, uploads it, records it, and returns the record.
- **R7.8** Documents are uploaded to object storage (MinIO/S3) through the existing `StorageService` port, under the folder `audit-reports`, using the same key convention as inbound uploads: `private/audit-reports/{username}/{uuid}_{filename}`. The `private/` prefix is **mandatory, not cosmetic** — `GET /api/files/url` resolves a path only if it literally starts with `private/` or `public/`. Reports are private because they carry usernames, IP addresses and administrative history.
- **R7.9** Every archived document is recorded in `t_audit_report_file` with, at minimum, its **`file_path`** (the storage key), report type, format, file name, content type, size, row count, a JSON snapshot of the filters used, the reporting window, and who generated it when. The record is only written **after** a successful upload — no row may point at a file that was never stored.
- **R7.10** `GET /api/auth/reports/archives` lists archived reports (paginated, newest first, filterable by `reportType`, `format`, `generatedBy`).
- **R7.11** No download link is persisted. Clients take `file_path` from the record and call the existing `GET /api/files/url?filePath=...` to mint a presigned link on demand, because presigned URLs expire (default 300s).

### 1.8 SDK contract

- **R8.1** Java client SDK adds `AuditReportsApi` with 3 JSON methods, 9 export methods (PDF/XLSX/CSV × 3 reports, returning `InputStream`), 3 archive methods and the archive list. Bumps to `0.2.0-beta.11`. Republishes to Nexus.
- **R8.2** TypeScript client mirrors: `AuditReportsClient` with matching method signatures + all new DTOs and enums (including `AuditReportFile`, `ReportType`, `ReportFormat`). Bumps to `0.2.0-beta.13`. Republishes via `pnpm publish`.
- **R8.3** The pre-existing `AuthAuditLog*` types in the client SDKs are renamed / consolidated onto the SecurityAudit types (matches R1.1 on the backend).

## 2. Non-functional requirements

### 2.1 Performance

- **N1** Report list endpoints must return first page (default size 20) in < 500ms p95 against a 1M-row audit table with all filters applied. Indexed on `(settings_area, timestamp DESC)`, `(application_module, timestamp DESC)`, `(user_id, timestamp DESC)`, `(timestamp DESC)`.
- **N2** Excel **download** of 100k rows must complete in < 30s and consume < 256MB of heap. Two documented carve-outs, both structural rather than implementation defects:
  - **the archive path (R7.7) does not meet this.** `StorageService.uploadFile` accepts `byte[]` only — there is no stream overload — so archiving necessarily materializes the whole document in memory. Archive requests are expected to cover report-sized windows, not 100k-row dumps.
  - **the Excel in-memory fallback (R7.6) does not meet this** either, by design: it exists to keep exports working where no writable scratch directory is available, and is the degraded mode.
- **N3** PDF export of 10k rows must complete in < 20s.
- **N3.1** CSV export is the cheapest path — no workbook model, no temp files, flat memory at any row count — and is the recommended format for large extracts.

### 2.2 Reliability

- **N4** Audit write failure never breaks the calling user-facing action. All emitters catch and log; nothing rethrows.
- **N5** Chain-lock contention under peak load (~100 concurrent writes) must not exceed 1s p99 wait time. Postgres advisory lock is cheap; verify in load test.

### 2.3 Backwards compatibility

- **N6** The current 3 endpoints (`GET /api/auth/audit`, `GET /api/auth/audit/{id}`, `GET /api/auth/audit/user/{userId}`) collapse to one paginated `GET /api/auth/audit` with filters. **This is a breaking SDK change** — mandated by R1.3 unification. The spec's "keep the 3 current endpoints unchanged" line is explicitly overridden.
- **N7** Existing rows in `t_security_audit_log` keep their `contextData` JSON blob. New rows write only the typed columns (`settings_area`, `entity_name`, `previous_value`, `new_value`); `contextData` is written as `NULL` for new rows and marked deprecated in the entity. Migration to drop the column is deferred to a `0.3.0` cleanup pass (see roadmap).

### 2.4 Security

- **N8** Chain secret (`AUDIT_CHAIN_SECRET`) is a required environment variable in production. Missing → refuse to start.
- **N9** Rehash-on-boot flag (`igrp.audit.chain.rehash-on-boot=true`) is refused when Spring profile `prod` is active — logged as an error, ignored.
- **N10** Neither raw IP nor identifier value is written to application logs (only to the audit table). Tamper-detection error logs from MR 362 already scrub hash values from log lines.
- **N11** Archived reports are stored under `private/` and are only reachable through a short-lived presigned URL (R7.8/R7.11). No archived report is ever written to a public object.
- **N12** *(open)* CSV export does not neutralise spreadsheet formula injection. Audit rows carry user-influenced text (`entityName`, `previousValue`, `newValue`); a field like `=1+1` round-trips as-is and RFC 4180 quoting does not stop Excel evaluating it on open. Neutralising means prefixing `'`, which mutates the value for programmatic consumers — a product decision, deliberately not taken silently. Revisit before CSV is exposed to untrusted report consumers.

## 3. Design decisions (locked)

| Decision | Choice | Reason |
|---|---|---|
| IP storage | Raw + hashed (both columns) | User confirmed raw is fine; hash retained to keep tamper detection intact even if raw IP is edited. |
| Unification target | Keep `SecurityAuditLog` names + `t_security_audit_log` table; delete `AuthAuditLog` | Less disruption; `SecurityAuditLog` already has 34 event types and 6 categories covering everything MR 362 does. |
| Endpoint contract for 3 legacy GETs | Collapse to one filtered list | Explicit user directive; spec's "keep unchanged" line overridden. |
| Hash chain rewrite on schema change | Rehash-on-boot flag (dev/staging only) | Preserves tamper evidence for new columns; production never rehashes. |
| Permission granularity | Single `igrp.audit.view` for reads + reports + exports | Reports are aggregations over the raw log; splitting the permission adds no security value. |
| Admin action instrumentation | Domain events + `SettingsAuditEventListener` | Cleaner and more testable than AOP annotations; each handler publishes its own strongly-typed event. |
| `SecurityAuditLog` PK type | UUID (converted from Long in Phase 1) | Aligns with MR 362's tamper-evidence design; sequence_number becomes the ordering key. Avoids Long-vs-UUID inconsistency across the audit surface. |
| `contextData` JSON column | Kept on existing rows; new rows write `NULL` and use typed columns only | See N7. Drop deferred to `0.3.0`. |
| Async ordering | Sync writes with `@Transactional(REQUIRES_NEW)` + Postgres advisory lock | Preserves "audit committed before caller sees success" guarantee. Advisory lock serializes chain writes cluster-wide. |
| `SessionAuditLogger` | Unchanged | Already calls `SecurityAuditService`; inherits the hash chain automatically. |
| Catalog-gap handlers | Skip with `TODO`, don't create in this feature | Extracting activate/deactivate/invite handlers is its own design task. |
| PDF library | OpenPDF 2.0.3 (LGPL) | Apache POI covers Excel; OpenPDF is the LGPL-safe fork of iText 4. AGPL iText is off the table. Note the artifact is `com.github.librepdf:openpdf` — `openpdf-core-legacy` does not exist on Maven Central; 2.0.x still ships the legacy `com.lowagie.text` package. |
| `commons-io` version | Pinned to 2.16.1 | POI 5.3.0 needs `BoundedInputStream.builder()` (≥ 2.16); an older transitive 2.14.0 otherwise wins and Excel fails at runtime with `NoSuchMethodError`. |
| Third export format | CSV | Asked for by the product owner. Also the only format with no library, no temp file and no workbook model — so it doubles as the dependency-free fallback when Excel generation is degraded. |
| Excel with no writable temp dir | Fall back to in-memory `XSSFWorkbook` | SXSSF cannot stream without disk. Failing the request is worse than using more heap; the alternative (requiring infra everywhere) makes the endpoint environment-dependent. Streaming stays the default and preferred path. |
| Archive delivery | Store + return `file_path`, don't return bytes or a link | Direct download already covers "give me the file now". Archiving answers "keep it and let me find it later". A persisted link would expire; the path does not. |
| Archive upload path | Call `StorageService` directly, not `UploadFileCommandHandler` | That handler takes a `MultipartFile` (an inbound upload); archived reports are generated server-side. The key convention is mirrored so both land in the same shape. |
| Archive record vs. re-generation | Persist metadata, not the row data | The document is the artefact; `t_security_audit_log` remains the source of truth. The record exists to locate the file, not to reconstruct it. |
| TS SDK publish | `pnpm publish` | Existing workflow. |
