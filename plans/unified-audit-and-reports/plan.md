# Plan — Unified Audit & Reports

Companion documents: [`requirements.md`](./requirements.md), [`validation.md`](./validation.md).

Delivered incrementally across 5 phases, each independently mergeable to `version/0.2.0-beta`.

---

## Phase 1 — Rewire MR 362 hash chain onto `SecurityAuditLog`

**Goal:** collapse `AuthAuditLog` into `SecurityAuditLog`; add tamper-evident hash chain to the unified table. No new endpoints, no report fields yet.

**Approach:** rather than merging MR 362, cherry-pick its hash-chain infrastructure and adapt it to `SecurityAuditLog`'s existing shape (Long PK, 34 event types, 6 categories, existing columns).

### Steps

1. **Schema migration** `V10_1__add_hash_chain_to_security_audit_log.sql`:
   - **Convert PK Long → UUID** (safe for existing data):
     ```sql
     ALTER TABLE t_security_audit_log ADD COLUMN id_uuid UUID NOT NULL DEFAULT gen_random_uuid();
     ALTER TABLE t_security_audit_log DROP CONSTRAINT t_security_audit_log_pkey;
     ALTER TABLE t_security_audit_log DROP COLUMN id;
     ALTER TABLE t_security_audit_log RENAME COLUMN id_uuid TO id;
     ALTER TABLE t_security_audit_log ADD PRIMARY KEY (id);
     ```
     If prod already has rows, existing IDs are lost — this is acceptable because (a) audit rows are referenced by no other FK, (b) the new `sequence_number` becomes the stable ordering key, (c) it's the only chance to align on UUID before the hash chain locks the shape.
   - Add `sequence_number BIGINT GENERATED ALWAYS AS IDENTITY`, `previous_hash VARCHAR(64) NOT NULL DEFAULT ''`, `current_hash VARCHAR(64) NOT NULL DEFAULT ''`, `epoch_ms BIGINT`, `ip_hash VARCHAR(64)` to `t_security_audit_log`.
   - `CREATE UNIQUE INDEX ux_security_audit_sequence ON t_security_audit_log (sequence_number)`.
   - Seed a GENESIS row (sequence 0, hash of `chain_secret`).
   - Add append-only trigger: reject `UPDATE`/`DELETE` unless a session variable `app.audit_purge = 'true'` is set (used only by the purge command).
2. **Domain**: change `SecurityAuditLog.id` from `Long` → `UUID` with `@GeneratedValue(strategy = GenerationType.UUID)`. Update `SecurityAuditLogRepository` to `JpaRepository<SecurityAuditLog, UUID>`. Update all query handlers, controller path variables (`GET /api/auth/audit/{id}` — deleted anyway per R1.3), DTOs (`SecurityAuditLogDTO.id` → String/UUID), and any tests that stub Long IDs. Extend `SecurityAuditLog` entity with the 5 new columns. Add `SecurityAuditChainService` (port of `AuthAuditChainService`, renamed) that:
   - Reads the current chain tip inside a Postgres `pg_advisory_xact_lock(<constant>)`.
   - Computes `current_hash = HMAC-SHA256(secret, previous_hash || serialize(fields))`.
   - Inserts the row in the same transaction.
3. **Wire chain service into `SecurityAuditServiceImpl`**: every `log*` method routes through `chainService.append(...)`.
4. **Port MR 362 event listeners** (`AuthAuditEventListener`, `AuthAuditFailureListener`, `AuthAuditLogoutListener`) into `security_audit/**/config/`, rewired to call `SecurityAuditService` and to publish `AuditEventType` values from the existing 34-value enum (not `AuthEventType`, which is deleted).
5. **Delete** `shared/domain/audit/AuthAuditLog.java`, `AuthEventType.java`, `AuthAuditContext.java`, `IdentifierType.java`, `AuthAuditLogRepository.java`, `AuthAuditLogSpecification.java`, `AuthAuditService.java`, and `AuthAuditLogSummaryDTO.java`. Delete their tests.
6. **Rewrite `AuthAuditController`** to expose one paginated + filtered `GET /api/auth/audit` (delete the `/{id}` and `/user/{userId}` methods per R1.3 / N6). Add `DELETE /api/auth/audit/purge` gated by `igrp.audit.purge`.
7. **Add `SecurityAuditChainValidator`** service (port of MR 362's) — walks the table and verifies each row's hash. Expose via `POST /api/auth/audit/validate` for administrators (gated by `igrp.audit.view`).
8. **Environment**: `AUDIT_CHAIN_SECRET` becomes required in prod (`@Value("${igrp.audit.chain.secret}")` with startup validation). Add to `.env.example`.
9. **Rehash flag**: `igrp.audit.chain.rehash-on-boot=false` default. When `true` and profile != `prod`, `SecurityAuditChainService.rehashAll()` runs on `ApplicationReadyEvent`.

### Files touched (approximate)

| Kind | Count | Notes |
|---|---|---|
| Java added | ~8 | Chain service, validator, listeners (3), permission constants (extend existing), migration runner hook |
| Java modified | ~8 | `SecurityAuditLog` entity (Long→UUID + chain cols), `SecurityAuditLogRepository`, `SecurityAuditLogDTO`, all `GetSecurityAuditLog*QueryHandler`s (Long→UUID signatures), `SecurityAuditServiceImpl`, `AuthAuditController`, `AuditPermissions`, one config class |
| Java deleted | ~10 | The entire `shared/**/audit/*` and `shared/**/AuthAudit*` set |
| SQL migrations | 1 | `V10_1__add_hash_chain_to_security_audit_log.sql` |
| Tests added | ~6 | Chain service, validator, listener (×3), controller |
| Tests deleted | ~5 | AuthAudit* tests |

### Deliverable

Single commit series on `version/0.2.0-beta` that leaves the audit surface functionally identical to today (same endpoint contract for consumers who only use `GET /api/auth/audit`) but with tamper-evidence added and the duplicate `AuthAuditLog` gone.

---

## Phase 2 — Schema extension + 3 report endpoints

**Goal:** the 3 JSON report endpoints from the spec, backed by new typed columns on `t_security_audit_log`.

### Steps

1. **Schema migration** `V11_1__extend_security_audit_log_for_reports.sql`:
   ```sql
   ALTER TABLE t_security_audit_log
       ADD COLUMN application_module    VARCHAR(100),
       ADD COLUMN access_role            VARCHAR(255),
       ADD COLUMN operation_state        VARCHAR(50),
       ADD COLUMN device                 VARCHAR(255),  -- reserved; derived at read time
       ADD COLUMN authorized_by          VARCHAR(255),
       ADD COLUMN status                 VARCHAR(20),
       ADD COLUMN action                 VARCHAR(100),
       ADD COLUMN settings_area          VARCHAR(20),
       ADD COLUMN settings_entity_type   VARCHAR(30),
       ADD COLUMN settings_operation     VARCHAR(30),
       ADD COLUMN entity_name            VARCHAR(500),
       ADD COLUMN related_entity         VARCHAR(500),
       ADD COLUMN previous_value         TEXT,
       ADD COLUMN new_value              TEXT,
       ADD COLUMN period_start           TIMESTAMPTZ,
       ADD COLUMN period_end             TIMESTAMPTZ;

   CREATE INDEX idx_audit_settings_area  ON t_security_audit_log (settings_area, timestamp DESC)
       WHERE settings_area IS NOT NULL;
   CREATE INDEX idx_audit_module         ON t_security_audit_log (application_module, timestamp DESC)
       WHERE application_module IS NOT NULL;
   ```

2. **Update `SecurityAuditChainService.buildHashInput(...)`** to serialize the new columns. This breaks chains in dev/staging DBs. Guard with the rehash-on-boot flag (Phase 1 gave us that lever).

3. **Enums** (in `security_audit/domain/enums/`):
   - `AuditStatus { SUCCESS, ACCESS_DENIED, UNUSUAL_IP, PENDING, ERROR }`
   - `SettingsArea { APPLICATIONS, USERS, ACCESS }`
   - `SettingsEntityType { APPLICATION, USER, DEPARTMENT, ROLE, PERMISSION, MENU }`
   - `SettingsOperation { CREATE, DELETE, EDIT, ACTIVATE, DEACTIVATE, INVITE, CANCEL_INVITE, RESEND_INVITE, ASSOCIATE, DISASSOCIATE, ASSIGN, UNASSIGN }`

4. **Extend `SecurityAuditServiceImpl`**:
   - Add `logSettingsEvent(area, entityType, operation, entityName, relatedEntity, previousValue, newValue)`.
   - Extend `logEvent(...)` overloads for the audit/access report context.

5. **New controller** `AuditReportsController` at `/api/auth/reports`, three methods gated by `@PreAuthorize("@igrpAuthorization.checkPermission(T(Permission).IGRP_AUDIT_VIEW)")`:
   - `GET /audit` → `Page<AuditReportRowDTO>`
   - `GET /access` → `Page<AccessReportRowDTO>`
   - `GET /settings` → `Page<SettingsReportRowDTO>`

6. **Query handlers** (`GetAuditReportQueryHandler` etc.) delegate to a shared `SecurityAuditSpecificationBuilder` translating each filter into JPA `Specification<SecurityAuditLog>`.

7. **Response DTOs** exactly match the JSON examples in the source spec. `device` field derived from `user_agent` via a small `UserAgentParser` (Chrome/Firefox/Edge/Safari × Windows/macOS/Linux/Android/iOS lookup table).

8. **`.igrpstudio` JSON**:
   - `.igrpstudio/audit/controllers/AuditReportsController.json`
   - `.igrpstudio/audit/dto/{AuditReportRowDTO,AccessReportRowDTO,SettingsReportRowDTO}.json`

9. **Permission seeding**: `V12_1__seed_audit_reports_permissions.sql` — insert `igrp.audit.view` (if missing) and grant to `DEPT_IGRP.Administrator`.

### Deliverable

Three new REST endpoints returning empty result sets against a fresh DB (no admin instrumentation yet; Phase 3 fills them). Auth events already recorded from Phase 1 make the Audit and Access reports non-empty in local testing.

---

## Phase 3 — Domain events for admin actions (Settings Report data)

**Goal:** every catalog-3.1 action publishes a strongly-typed Spring `ApplicationEvent`; a single listener writes to `SecurityAuditService`.

### Design

- **Event marker interface** `SettingsAuditEvent` in `security_audit/domain/events/` — carries `area`, `entityType`, `operation`, `entityName`, `relatedEntity`, `previousValue`, `newValue`, `status`.
- **Concrete events**: one class per catalog row, ~28 total. Example:
  ```java
  public record ApplicationCreatedEvent(String applicationName) implements SettingsAuditEvent {
      public SettingsArea area()            { return SettingsArea.APPLICATIONS; }
      public SettingsEntityType entityType(){ return SettingsEntityType.APPLICATION; }
      public SettingsOperation operation()  { return SettingsOperation.CREATE; }
      public String entityName()            { return applicationName; }
      public String relatedEntity()         { return null; }
      public String previousValue()         { return null; }
      public String newValue()              { return null; }
      public AuditStatus status()           { return AuditStatus.SUCCESS; }
  }
  ```
- **Publishing**: each admin command handler gets one line — `applicationEventPublisher.publishEvent(new ApplicationCreatedEvent(cmd.name()))` after the successful DB write, before returning.
- **Listener**: `SettingsAuditEventListener` (`@Component`, `@Async`) with a single `@EventListener` method taking `SettingsAuditEvent` — calls `securityAuditService.logSettingsEvent(...)` extracting all fields.

### Catalog → handler mapping

| Catalog row | Handler | Event class |
|---|---|---|
| Add application | `CreateApplicationCommandHandler` | `ApplicationCreatedEvent` |
| Edit application | `UpdateApplicationCommandHandler` | `ApplicationEditedEvent` (carries previous/new values captured before the update) |
| Remove application | `DeleteApplicationCommandHandler` | `ApplicationDeletedEvent` |
| Activate application | *(gap — TODO)* | `ApplicationActivatedEvent` (class defined; no publisher yet) |
| Deactivate application | *(gap — TODO)* | `ApplicationDeactivatedEvent` |
| Invite user | *(gap — TODO)* | `UserInvitedEvent` |
| Cancel invite | *(gap — TODO)* | `UserInviteCancelledEvent` |
| Resend invite | *(gap — TODO)* | `UserInviteResentEvent` |
| Activate user | *(gap — TODO)* | `UserActivatedEvent` |
| Deactivate user | *(gap — TODO)* | `UserDeactivatedEvent` |
| Create department | `PostDepartmentCommandHandler` | `DepartmentCreatedEvent` |
| Edit department | `UpdateDepartmentCommandHandler` | `DepartmentEditedEvent` |
| Remove department | `DeleteDepartmentCommandHandler` | `DepartmentDeletedEvent` |
| Activate department | *(gap — TODO)* | — |
| Deactivate department | *(gap — TODO)* | — |
| Create role | `CreateRoleCommandHandler` | `RoleCreatedEvent` |
| Edit role | `UpdateRoleCommandHandler` | `RoleEditedEvent` |
| Remove role | `DeleteRoleCommandHandler` | `RoleDeletedEvent` |
| Activate role | *(gap — TODO)* | — |
| Deactivate role | *(gap — TODO)* | — |
| Associate permission to role | `AddPermissionsCommandHandler` | `PermissionAssociatedToRoleEvent` |
| Remove permission from role | `RemovePermissionsCommandHandler` | `PermissionDisassociatedFromRoleEvent` |
| Associate application to role | `AddApplicationsToDepartmentCommandHandler` | `ApplicationAssociatedToRoleEvent` |
| Disassociate application from role | `RemoveApplicationsFromDepartmentCommandHandler` | `ApplicationDisassociatedFromRoleEvent` |
| Associate menu to role | `AddMenusToDepartmentCommandHandler` | `MenuAssociatedToRoleEvent` |
| Disassociate menu from role | `RemoveMenusFromDepartmentCommandHandler` | `MenuDisassociatedFromRoleEvent` |
| Assign role to user | *(locate handler)* | `RoleAssignedToUserEvent` |
| Unassign role from user | *(locate handler)* | `RoleUnassignedFromUserEvent` |

**Gap policy:** the event classes are defined for every catalog row (so the report enum values are complete), but rows marked *(gap)* have no publisher until the corresponding handlers are extracted. Marked with `TODO(catalog-gap)` in the handler file where the action currently lives folded into an `Update*` command.

### `previousValue` / `newValue` capture

For `EDIT` events, the handler must load the entity before the update, capture the changed field(s) as a compact string (e.g., `"name=old→new; status=active→inactive"`), pass it to the event constructor.

### Tests

- `SettingsAuditEventListenerTest` — one test per event class → assert `securityAuditService.logSettingsEvent(...)` called with correct arguments.
- `AppSettingsAuditIT` / `DepartmentSettingsAuditIT` / etc. — Spring integration tests that run the handler through the full stack and assert a row lands in `t_security_audit_log`.

---

## Phase 4 — PDF + Excel export

**Goal:** 6 new endpoints (3 reports × 2 formats).

### Steps

1. **Add dependencies** to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.apache.poi</groupId>
       <artifactId>poi-ooxml</artifactId>
       <version>5.3.0</version>
   </dependency>
   <dependency>
       <groupId>com.github.librepdf</groupId>
       <artifactId>openpdf-core-legacy</artifactId>
       <version>2.0.3</version>
   </dependency>
   ```

2. **`ReportColumnDefinition`** value object per report type: label, extractor lambda, format hint.

3. **`ExcelReportExporter`** — uses `SXSSFWorkbook` (streaming, 100-row window in memory). Writes header row, streams data rows via a `Stream<T>` from the repository.

4. **`PdfReportExporter`** — OpenPDF `PdfWriter` + `PdfPTable`. First page carries the header text ("Audit Report generated on YYYY-MM-DD") from `MessageSource`.

5. **6 new controller methods** on `AuditReportsController`:
   ```
   GET /api/auth/reports/audit.pdf    | .xlsx
   GET /api/auth/reports/access.pdf   | .xlsx
   GET /api/auth/reports/settings.pdf | .xlsx
   ```
   Accept same filters as JSON siblings, minus pagination. Return `StreamingResponseBody`.

6. **Content-Disposition**: `attachment; filename="audit-report-YYYY-MM-DD.xlsx"` (etc.).

### Tests

- Golden-file tests: generate a report against a fixed 5-row seed → compare byte-for-byte to a checked-in fixture (with a regex-based scrub for the header timestamp).
- Load test (manual, not CI): 100k rows → assert memory + timing per §2.1 N2/N3.

---

## Phase 5 — Java + TypeScript SDK updates

### Java client (`modules/client`)

1. Add `cv.igrp.platform.access.client.api.AuditReportsApi`:
   - `Page<AuditReportRowDTO> getAuditReport(filters, pageable)`
   - `Page<AccessReportRowDTO> getAccessReport(filters, pageable)`
   - `Page<SettingsReportRowDTO> getSettingsReport(filters, pageable)`
   - `InputStream getAuditReportPdf(filters)` × 3 reports
   - `InputStream getAuditReportXlsx(filters)` × 3 reports
2. Add DTOs + enums to `cv.igrp.platform.access.client.model.reports.*`.
3. Rename `SecurityAuditLog*` → `AuditLog*` for clarity (SDK-visible breaking change consistent with R8.3).
4. Add `AuditLogClientApi` with `list(filters, pageable)`, `validate()`, `purge()`.
5. Tests using `RestClient` mock — one per method.
6. Bump `client` version `0.2.0-beta.10 → 0.2.0-beta.11`. Bump parent `backend 0.2.0-beta.5 → 0.2.0-beta.6`. Update inter-module deps in `core-spring-boot`.
7. `mvn deploy` to Nexus (releases repo).

### TypeScript client (`access-management/frontend/packages` submodule)

1. Locate the audit module (verify path during Phase 5 execution — likely under `packages/*/src/api/`).
2. Add `AuditReportsClient` mirroring the Java API.
3. Add TS types for all DTOs + enums.
4. Rename `SecurityAuditLog*` → `AuditLog*`.
5. Bump package `0.2.0-beta.12 → 0.2.0-beta.13`.
6. `pnpm publish` to Nexus npm-hosted repo.

### Deliverable

Both SDKs published; consumers can drop-in upgrade and get the new endpoints.

---

## Effort estimate

| Phase | Wall clock | Risk |
|---|---|---|
| 1 | 1.5–2h | Low — rewire, don't rebuild |
| 2 | 3–4h | Med — hash chain rewrite |
| 3 | 2–3h | Med — catalog-gap policy |
| 4 | 2–3h | Low |
| 5 | 2h Java + 2–3h TS | Low — mostly mechanical |

Total ~12–17h across 3–5 focused sessions.

## Sequencing

Phases must land in order (each depends on the previous). Phase 5 can happen in parallel with Phase 4 verification since the SDK doesn't consume the export endpoints as data — it just proxies them.
