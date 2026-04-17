---
goal: Temporary Role Assignment Support
version: 1.0
date_created: 2026-04-15
last_updated: 2026-04-15
status: 'Planned'
tags: [feature, enhancement, authorization]
---

# Introduction

This plan introduces a temporary role assignment mechanism in the iGRP Access Management system. The solution replaces the existing `@ManyToMany` relationship between users and roles with an explicit association entity (`UserRoleAssignment`) that supports expiration (`expiresAt`).

Roles are physically assigned to users and automatically revoked upon expiration using a combination of scheduled tasks and runtime validation. Historical traceability is handled exclusively through the existing `SecurityAuditLogEntity`, eliminating the need for a `status` field in the assignment table.

The architecture ensures strong consistency, auditability, and compatibility with stateless JWT-based authentication.

## 1. Requirements & Constraints

* **REQ-001**: The system must support assigning roles to users with an optional expiration date (`expiresAt`).
* **REQ-002**: Expired roles must be automatically revoked without manual intervention.
* **REQ-003**: The existing `@ManyToMany` relationship between User and Role must be replaced with an explicit association entity.
* **REQ-004**: The system must not use a `status` field in the role assignment table.
* **REQ-005**: All role assignment, expiration, and revocation events must be logged using `SecurityAuditLogEntity`.
* **REQ-006**: Authorization logic must ignore expired assignments even if cleanup jobs fail.
* **REQ-007**: The system must support both permanent (`expiresAt = null`) and temporary roles.
* **PAT-001**: Align with iGRP Studio metadata by updating `.igrpstudio` model definitions accordingly.
* **PAT-002**: Follow stateless JWT principles (no roles embedded in token).

## 2. Implementation Steps

### Implementation Phase 1

* GOAL-001: Replace ManyToMany with explicit UserRoleAssignment entity and migrate data.

| Task     | Description                                                                                                                                                                 | Completed | Date |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------- | ---- |
| TASK-001 | Create Flyway migration `V4__migrate_user_roles_to_user_role_assignment.sql` to create `user_role_assignment` table with `user_id`, `role_id`, `assigned_at`, `expires_at`. |           |      |
| TASK-002 | Migrate existing data from `user_roles` into `user_role_assignment` with `expires_at = NULL`.                                                                               |           |      |
| TASK-003 | Backup or rename `user_roles` table to `user_roles_backup`.                                                                                                                 |           |      |
| TASK-004 | Create `UserRoleAssignment` entity and composite key (`UserRoleId`).                                                                                                        |           |      |
| TASK-005 | Update `.igrpstudio/shared/models/UserRoleAssignment.json`.                                                                                                                 |           |      |

### Implementation Phase 2

* GOAL-002: Refactor domain and persistence layer to use the new model.

| Task     | Description                                                                            | Completed | Date |
| -------- | -------------------------------------------------------------------------------------- | --------- | ---- |
| TASK-006 | Remove `@ManyToMany` mapping from User and Role entities.                              |           |      |
| TASK-007 | Add `@OneToMany` relationship in User → `UserRoleAssignment`.                          |           |      |
| TASK-008 | Add `@OneToMany` relationship in Role → `UserRoleAssignment`.                          |           |      |
| TASK-009 | Create `UserRoleAssignmentRepository` with queries for active and expired roles.       |           |      |
| TASK-010 | Implement query filtering: `(expires_at IS NULL OR expires_at > NOW())`.               |           |      |
| TASK-011 | Update `RoleDTO` to include the field `expiresAt` (nullable).                          |           |      |
| TASK-012 | Adjust mapping logic (Entity → DTO) to populate `expiresAt` from `UserRoleAssignment`. |           |      |
| TASK-013 | Ensure API responses include `expiresAt` when returning user roles.                    |           |      |
| TASK-014 | Update `.igrpstudio` model definitions for `RoleDTO` to include `expiresAt`.           |           |      |

### Implementation Phase 3

* GOAL-003: Implement role assignment, revocation, and expiration logic.

| Task     | Description                                                                                                              | Completed | Date |
| -------- | ------------------------------------------------------------------------------------------------------------------------ | --------- | ---- |
| TASK-011 | Update `AddRolesToUserCommandHandler` to supporting optional `expiresAt`.                                                |           |      |
| TASK-012 | Update`RemoveRolesFromUserCommandHandler` to support manual role unassignment to launch an audit event of role revoking. |           |      |
| TASK-013 | Implement `ExpireRoleService` to delete expired assignments.                                                             |           |      |
| TASK-014 | Integrate `TaskScheduler` to schedule expiration at `expiresAt`.                                                         |           |      |
| TASK-015 | Implement fallback `@Scheduled` job to delete expired assignments periodically.                                          |           |      |

### Implementation Phase 4

* GOAL-004: Integrate audit logging using SecurityAuditLogEntity.

| Task     | Description                                                      | Completed | Date |
| -------- | ---------------------------------------------------------------- | --------- | ---- |
| TASK-016 | Log role assignment event (`ROLE_ASSIGNED`) with context data.   |           |      |
| TASK-017 | Log role expiration event (`ROLE_EXPIRED`) when auto-removed.    |           |      |
| TASK-018 | Log manual revocation event (`ROLE_REVOKED`).                    |           |      |
| TASK-019 | Include `correlationId`, `userId`, and request metadata in logs. |           |      |

### Implementation Phase 5

* GOAL-005: Enforce authorization and runtime validation.

| Task     | Description                                                          | Completed | Date |
| -------- | -------------------------------------------------------------------- | --------- | ---- |
| TASK-020 | Refactor role resolution logic to use `UserRoleAssignment`.          |           |      |
| TASK-021 | Ensure all authorization checks filter expired roles at query level. |           |      |
|          |                                                                      |           |      |

## 3. Dependencies

* **DEP-001**: Existing `SecurityAuditLogEntity` for audit logging.
* **DEP-002**: Spring `TaskScheduler` for precise expiration scheduling.
* **DEP-003**: Existing IAM role resolution mechanism.

## 4. Files

* **FILE-001**: `src/main/resources/db/migration/V4__migrate_user_roles_to_user_role_assignment.sql`
* **FILE-002**: `cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/UserRoleAssignment.java`
* **FILE-003**: `cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/UserRoleId.java`
* **FILE-004**: `.igrpstudio/shared/models/UserRoleAssignment.json`
* **FILE-005**: `cv/igrp/platform/access_management/shared/infrastructure/persistence/repository/UserRoleAssignmentRepository.java`
* **FILE-006**: `cv/igrp/platform/access_management/users/application/commands/AddRolesToUserCommandHandler.java`
* **FILE-007**: `cv/igrp/platform/access_management/users/application/commands/RemoveRolesFromUserCommandHandler.java`
* **FILE-008**: `cv/igrp/platform/access_management/users/infrastructure/service/ExpireRoleService.java`

## 5. Testing

* **TEST-001**: Assign a temporary role and verify it is available before expiration.
* **TEST-002**: Verify automatic removal after expiration (scheduler execution).
* **TEST-003**: Simulate scheduler failure and ensure expired roles are ignored at runtime.
* **TEST-004**: Validate audit logs for assignment, expiration, and revocation.
* **TEST-005**: Test concurrent requests during expiration boundary (race condition).

## 6. Risks & Assumptions

* **ASSUMPTION-001**: System clock is synchronized (UTC recommended).

* **ASSUMPTION-002**: Audit logging is reliable and append-only.

* **RISK-001**: Scheduler failure may delay role removal → mitigated by runtime validation.

* **RISK-002**: High-frequency role checks may impact performance → mitigated via indexing and caching.

* **RISK-003**: Data inconsistency during migration → mitigated by phased rollout and backup table.