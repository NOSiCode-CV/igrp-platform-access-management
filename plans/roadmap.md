# Roadmap

## Current stream: `0.2.0-beta`

Active branch: `version/0.2.0-beta`.
Java client SDK: `cv.igrp.platform.access:client:0.2.0-beta.10`.
TypeScript client: `0.2.0-beta.12`.
Backend parent: `cv.igrp.framework.auth:backend:0.2.0-beta.5`.

## Recently landed

| When | What |
|---|---|
| 2026-07 | **Java 26 migration.** All poms bumped 25 → 26 (API) and 21 → 26 (SDK). Published SDK 0.2.0-beta.5 / client 0.2.0-beta.10 to Nexus. |
| 2026-07 | **Default Service Account bootstrap.** On startup, the platform seeds a `igrp-access-management-sa` service account with all `igrp.client.*` and `igrp.service_account.*` permissions if absent. `.env.example` documents `IGRP_OAUTH_DEFAULT_SERVICE_ACCOUNT_*`. |
| 2026-07 | **ServiceAccountsApi** — full CRUD on service accounts via the client SDK. |
| 2026-Q2 | OAuth2 refresh-token rotation with revocation. |
| 2026-Q2 | Session limit enforcement + fan-in audit for session state changes (`SessionAuditLogger`). |

## In flight

### Unified Audit & Reports (`plans/unified-audit-and-reports/`)

Consolidates the two parallel audit systems (`SecurityAuditLog` and `AuthAuditLog`) into a single tamper-evident log, adds the 3 report endpoints demanded by the audit-reports specification, instruments administrative actions via domain events, and ships PDF/Excel export.

Owner: backend team.
Target: land on `version/0.2.0-beta` incrementally, one phase per merge.

### Department-Scoped Management (`plans/department-scoped-management/`)

Enforces subtree-scoped authorization on department + role write endpoints for non-superadmin users. Non-superadmins can only manage departments and roles within their own subtree (their department + descendants). Root-department creation stays superadmin-only. Read-side unchanged. Adds `GET /api/departments/manageable` for frontend pickers.

Owner: backend team.
Target: land on `version/0.2.0-beta`. Phases 1 & 3 can run in parallel with the audit feature; Phase 2 depends on the audit feature's Phase 3 (for `ACCESS_DENIED` Settings events).

## Next up (candidate)

| Priority | Item | Notes |
|---|---|---|
| High | **Activate / Deactivate / Invite command handlers.** Today status changes are folded into `Update*` handlers. Extracting them as first-class commands makes the audit catalog map cleanly and unlocks better UI affordances. | Deferred from Phase 3 of the audit-and-reports feature. |
| High | **Frontend integration** of the 3 report views (Audit, Access, Settings) in `application-center`. Blocked on Phase 5 of this feature landing. |
| Med | **Keycloak module GA** — `modules/keycloak-spring-boot` is currently on parent `0.2.0-beta.3`. Bring it forward, publish. |
| Med | **WSO2 module GA** — same treatment as Keycloak. |
| Med | **Permify module GA.** |
| Low | **Native image build** (GraalVM). The pom has profiles (`target-arm64`, `target-amd64`, `native`) already stubbed. |
| Low | **Deprecate `contextData` JSON column** on the audit log once all callers have migrated to the typed report columns (Phase 2 of the audit feature). |
| Low | **Scoped read endpoints** — extend department-scoped management to `GET` endpoints so scoped managers only see users/roles/permissions in their subtree. Deferred non-goal from the department-scoped-management feature. |
| Low | **Orphan department cleanup** — data repair for existing `t_department` rows whose `parent_id` points at deleted departments. Non-goal of the scoping feature; standalone data task. |

## Stream after `0.2.0-beta`

`0.3.0` — reserved for the frontend/backend contract refresh once the report views ship and stabilise. Breaking changes to the SDK go here.
