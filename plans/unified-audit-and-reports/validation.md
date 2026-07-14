# Validation — Unified Audit & Reports

How we know each phase is done. All checks must pass on the local JDK 26.0.1 build (`JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`, `MAVEN_OPTS="-Xmx1024m -XX:MaxMetaspaceSize=384m"`) before a phase is merged.

Companion documents: [`plan.md`](./plan.md), [`requirements.md`](./requirements.md).

---

## Phase 1 — Rewire hash chain onto `SecurityAuditLog`

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS with no `AuthAuditLog` references remaining (`grep -R AuthAuditLog src/main` returns empty).
- [ ] `mvnw test -Dtest=SecurityAuditChainServiceTest,SecurityAuditChainValidatorTest,AuthAuditController*Test,SecurityAuditServiceImplTest` — all green.
- [ ] All previously-existing `SecurityAudit*` tests still green (regression gate).

### Schema

- [ ] `V10_1__add_hash_chain_to_security_audit_log.sql` applies cleanly on a fresh Postgres 15 container (Testcontainers or `docker-compose up postgres`).
- [ ] `V10_1` also applies cleanly on a Postgres instance with rows in `t_security_audit_log` from a pre-migration seed — `id` column type is `UUID` afterwards, existing rows retain their data (only the ID value changes).
- [ ] After migration, `\d t_security_audit_log` shows `id UUID PRIMARY KEY`, the 5 new columns, and the unique index on `sequence_number`.
- [ ] GENESIS row present (`SELECT * FROM t_security_audit_log WHERE sequence_number = 0`).
- [ ] `INSERT` allowed. `UPDATE` and `DELETE` blocked (Postgres error mentions the trigger) except when `SET app.audit_purge = 'true'`.
- [ ] `SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, UUID>` compiles. Any old references to `findById(Long)` are gone.

### Behaviour

- [ ] Start the app locally. Log in via a browser → check `t_security_audit_log`: exactly one `LOGIN_SUCCESS` row with `previous_hash` = GENESIS's `current_hash`, `current_hash` computed correctly, `sequence_number = 1`.
- [ ] Fail a login → `LOGIN_FAILURE` row appended, chain intact.
- [ ] `POST /api/auth/audit/validate` returns `{ "valid": true, "rows_checked": <N> }`.
- [ ] Manually `UPDATE t_security_audit_log SET user_id='tampered' WHERE sequence_number=1;` (via `SET app.audit_purge='true'`) → validate returns `{ "valid": false, "broken_at": 1 }`.
- [ ] Concurrent write test: fire 50 parallel logins via a script → all 50 rows landed, sequence numbers 1..50 contiguous, chain valid.

### Configuration

- [ ] Missing `AUDIT_CHAIN_SECRET` in `prod` profile → app refuses to start with a clear error message.
- [ ] `igrp.audit.chain.rehash-on-boot=true` under `dev` profile → rehash runs on `ApplicationReadyEvent`, log message reports rows rewritten.
- [ ] Same flag under `prod` → refused, logged as error, ignored (chain not rewritten).

### Deletions

- [ ] `grep -R "class AuthAuditLog\|class AuthAuditService\|class AuthAuditContext\|enum AuthEventType\|enum IdentifierType" src/main` returns nothing.
- [ ] Studio JSON files under `.igrpstudio/shared/**/AuthAudit*` deleted.

---

## Phase 2 — Report endpoints

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=AuditReportsControllerIT,SecurityAuditSpecificationBuilderTest,UserAgentParserTest,GetAuditReportQueryHandlerTest,GetAccessReportQueryHandlerTest,GetSettingsReportQueryHandlerTest` — all green.

### Schema

- [ ] `V11_1__extend_security_audit_log_for_reports.sql` applies cleanly. All 16 new columns present. Two new partial indexes present.
- [ ] `V12_1__seed_audit_reports_permissions.sql` seeds `igrp.audit.view` (idempotent — re-runs cleanly on an existing DB).
- [ ] `DEPT_IGRP.Administrator` role has `igrp.audit.view` granted after fresh install.

### Hash chain integrity

- [ ] After Phase 2 migration, rows written in Phase 1 have their `current_hash` recomputed and match the new hash input formula (assuming `rehash-on-boot=true` was set once in dev).
- [ ] Validate endpoint returns `{ "valid": true }` post-rehash.

### API contract

For each of the 3 report endpoints:

- [ ] Call with only `startDate` (no `endDate`) → 400 with a clear error.
- [ ] Call with valid `startDate`/`endDate` and no other filters → 200, `Page` envelope with `content`, `totalElements`, `totalPages`, `size`, `number`, `first`, `last`.
- [ ] Call without `igrp.audit.view` permission → 403.
- [ ] Response DTO shape matches the JSON examples in `~/Downloads/audit-reports-requirements.md` (field names, casing, nullability).
- [ ] Each filter parameter narrows the result set as expected (smoke test with 3 filter values per report).

### `device` derivation

- [ ] `UserAgentParser.parse("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")` returns `"Chrome/Windows"`.
- [ ] Unknown UA returns `"Unknown"` (never null).

---

## Phase 3 — Domain event instrumentation

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS.
- [ ] `mvnw test -Dtest=SettingsAuditEventListenerTest,AppSettingsAuditIT,DepartmentSettingsAuditIT,RoleSettingsAuditIT` — all green.

### Behaviour — happy paths

For each command in the catalog table in `plan.md` §Phase 3 that has a handler today:

- [ ] Executing the command via its REST endpoint produces exactly ONE row in `t_security_audit_log` with `event_type=<matching>`, `settings_area`, `settings_entity_type`, `settings_operation`, `entity_name` set correctly.
- [ ] Row's `previous_value` and `new_value` populated for `EDIT` commands (verify shape: `field=old→new` compact string).
- [ ] Row's `related_entity` populated for `ASSOCIATE`/`DISASSOCIATE`/`ASSIGN`/`UNASSIGN` events.
- [ ] Row's `ip_address` and `user_agent` populated from the HTTP request.
- [ ] Hash chain still validates after the command completes (call validate endpoint).

### Behaviour — failure isolation

- [ ] Force the audit write to fail (e.g., truncate the audit table mid-request via a test hook) → the underlying admin command still returns success. Error is logged, not rethrown.
- [ ] Force the domain event listener to throw → the handler still returns success (async execution decouples them).

### Catalog gaps

- [ ] Every row in the plan's catalog table marked *(gap — TODO)* has:
  - the corresponding event class defined,
  - a `TODO(catalog-gap)` comment in the closest existing handler,
  - `roadmap.md` still lists "Activate / Deactivate / Invite command handlers" as the follow-up.

---

## Phase 4 — PDF + Excel export

### Compile & unit

- [ ] `mvnw -DskipTests compile` — BUILD SUCCESS (both new deps resolved from Maven Central via Nexus).
- [ ] `mvnw test -Dtest=ExcelReportExporterTest,PdfReportExporterTest,AuditReportsExportControllerTest` — all green.

### Golden-file

- [ ] Seed a fixed 5-row dataset. Generate `audit.xlsx`; open with Apache POI; assert cell values row-by-row against expected map.
- [ ] Same for `audit.pdf` — extract text with OpenPDF's `PdfReader`; assert header line matches `"Audit Report generated on \d{4}-\d{2}-\d{2}"` and body contains the 5 expected `username` values.
- [ ] Same 5-row assertion set for Access and Settings reports.

### Contract

- [ ] `Content-Type` = `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` for `.xlsx`; `application/pdf` for `.pdf`.
- [ ] `Content-Disposition` header = `attachment; filename="<report>-YYYY-MM-DD.<ext>"`.
- [ ] Same filter contract as JSON siblings (verify with `startDate`, `endDate`, one report-specific filter each).
- [ ] Missing `igrp.audit.view` → 403.

### Scale (manual, not CI)

- [ ] Generate `audit.xlsx` against 100k seeded rows → completes in < 30s, JVM heap stays under 256MB (verify with `jvisualvm`).
- [ ] Generate `audit.pdf` against 10k seeded rows → completes in < 20s.

---

## Phase 5 — SDK updates

### Java client

- [ ] `cd modules/client && mvn -DskipTests install` — BUILD SUCCESS with new `client 0.2.0-beta.11` in `~/.m2`.
- [ ] `mvn test -Dtest=AuditReportsApiTest,AuditLogClientApiTest` — all green.
- [ ] Written a small standalone Java `main` that constructs `AuditReportsApi`, calls `getAuditReport(...)` against the local running API → returns non-empty response, DTOs deserialise correctly.
- [ ] `mvn deploy` uploads `client-0.2.0-beta.11.jar`, `core-0.2.0-beta.6.jar`, `core-spring-boot-0.2.0-beta.6.jar`, `backend-0.2.0-beta.6.pom` to Nexus releases repo.
- [ ] `curl -I https://sonatype.nosi.cv/repository/igrp-framework-releases/cv/igrp/platform/access/client/0.2.0-beta.11/client-0.2.0-beta.11.jar` returns 200.

### TypeScript client

- [ ] `pnpm build` in the frontend packages submodule — success.
- [ ] `pnpm test` — passes; new client tests cover each report method.
- [ ] Manual smoke: import the built package into `application-center`, call each of the 3 report methods → returns typed data.
- [ ] `pnpm publish --registry=https://sonatype.nosi.cv/repository/igrp/` uploads the new version.
- [ ] `curl -I https://sonatype.nosi.cv/repository/igrp/<package-name>/-/<package-name>-0.2.0-beta.13.tgz` returns 200.

---

## Cross-phase — end-to-end scenarios

Run these after **all 5 phases** land:

1. **Login-to-audit-to-report**: log in → the `LOGIN_SUCCESS` row appears in the Audit Report at `GET /api/auth/reports/audit?startDate=today&endDate=today` within 1s.
2. **Admin-op-to-settings-report**: create an application → the `CREATE APPLICATION <name>` row appears in the Settings Report at `GET /api/auth/reports/settings?...` within 1s.
3. **Export symmetry**: JSON `GET /api/auth/reports/audit?startDate=X&endDate=Y` returns N rows → `GET /api/auth/reports/audit.xlsx?startDate=X&endDate=Y` has N data rows.
4. **Tamper detection end-to-end**: perform 10 admin actions; open psql, hack a row (`SET app.audit_purge='true'`; `UPDATE ...`); call validate → returns `{ valid: false, broken_at: <n> }`; audit reports still return data (validation failure doesn't kill the reads).
5. **SDK round-trip**: Java main and TS script both hit the 3 report endpoints via the published SDKs, get identical row counts for the same filters.
