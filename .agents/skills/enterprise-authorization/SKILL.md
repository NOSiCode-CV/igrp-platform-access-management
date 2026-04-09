---
name: "enterprise-authorization"
description: "Implements enterprise-grade authorization (ABAC, fine-grained permissions, enriched auditing, targeted caching). Invoke when refactoring authorization logic or applying IAM recommendations."
---

# Enterprise Authorization — Implementation Guide

Reference document: [ENTERPRISE_IAM_RECOMMENDATIONS.md](../../../../docs/ENTERPRISE_IAM_RECOMMENDATIONS.md)

---

## Scope

**In scope:**
- **User Model Expansion**: Add `nic` and `phoneNumber` to `IGRPUserDTO` and `IGRPUserEntity`.
- **Permission Standardization**: Refactor all permissions to `module.resource.action` format.
- **ABAC Policy Engine**: Implement a `Policy` interface and a 6-step decision pipeline in `IgrpAuthorizationService`.
- **Enterprise Auditing**: Capture decision reasons, correlation IDs, and request paths in `SecurityAuditService`.
- **Targeted Caching**: Refactor `PermissionCacheEvictService` to use granular eviction by subject, role, or department.

**Out of scope:**
- Modifying the underlying OIDC/JWT validation flow.
- Changing the fundamental Department-Role-User structure.
- Replacing the existing Redis-based caching infrastructure (only the invalidation logic changes).

---

## Implementation Phases

### 1. User Model & Identity Expansion
| File | Action |
|---|---|
| [IGRPUserDTO.java](../../../src/main/java/cv/igrp/platform/access_management/shared/application/dto/IGRPUserDTO.java) | Add `nic` (max 13) and `phoneNumber` (E.164 pattern). |
| [IGRPUserEntity.java](../../../src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/IGRPUserEntity.java) | Add `nic` and `phone_number` columns. |
| [IGRPUserMapper.java](../../../src/main/java/cv/igrp/platform/access_management/users/mapper/IGRPUserMapper.java) | Update `toDto`, `toBusinessDTO`, and `toEntity` mappings. |

**Validation**: Ensure NIC and Phone Number are treated as PII and restricted via specific permissions.

### 2. Permission Standardization
| Component | Action |
|---|---|
| `PermissionsRegistry` | Audit all enums and refactor to `module.resource.action` format. |
| `AuthorizationSyncService` | Verify DB synchronization of the new permission naming convention. |

### 3. ABAC Policy Engine & Pipeline
| File | Action |
|---|---|
| `Policy.java` (New) | Define the policy interface (Input: subject, action, resource context; Output: ALLOW/DENY + reason). |
| [IgrpAuthorizationService.java](../../../src/main/java/cv/igrp/platform/access_management/shared/security/IgrpAuthorizationService.java) | Implement the 6-step pipeline (AuthN -> Roles -> Perms -> Resource Context -> Policies -> Audit). |
| `OwnershipPolicy.java` (New) | Implement rule: `resource.ownerId == subject`. |
| `DepartmentScopePolicy.java` (New) | Implement rule: `subject has role in resource.department`. |

### 4. Enterprise Audit Logging
| File | Action |
|---|---|
| [SecurityAuditService.java](../../../src/main/java/cv/igrp/platform/access_management/security_audit/application/service/SecurityAuditService.java) | Update to log decision reasons and request context. |
| `SecurityAuditContextProvider.java` | Add correlation ID and request path capture. |

### 5. Performance & Caching
| File | Action |
|---|---|
| [PermissionCacheEvictService.java](../../../src/main/java/cv/igrp/platform/access_management/shared/infrastructure/cache/PermissionCacheEvictService.java) | Replace `evictAll` with targeted eviction logic (by subject, role, or department). |

---

## Acceptance Criteria (Definition of Done)

- [ ] `IGRPUserDTO` and `Entity` include `nic` and `phoneNumber`.
- [ ] Permissions follow the `module.resource.action` naming convention.
- [ ] ABAC policies evaluate ownership and departmental scope during authorization.
- [ ] Security audit logs contain decision reasons and correlation IDs.
- [ ] Cache invalidation occurs only for affected subjects/roles/departments.
- [ ] All `IGRPUserMapperTest` and related authorization tests pass.

---

## Troubleshooting

| Error | Reason | Solution |
| :--- | :--- | :--- |
| `LazyInitializationException` | Accessing resource context (e.g. `resource.department`) in the policy engine without a session. | Use `JdbcTemplate` or fetch-join the required context in the decision pipeline. |
| `LazyInitializationException` | Accessing `user.getRoles()` or `r.getPermissions()` collections outside of Hibernate session context. | **Critical Fix**: Replace all `userRepository.findByExternalId()` calls with `userRepository.findByExternalIdWithRolesAndPermissions()` which includes `left join fetch u.roles r left join fetch r.permissions p`. |
| Timeout/Request Hanging | Complex fetch join query causing performance issues. | Monitor query performance with `EXPLAIN ANALYZE` and consider splitting into separate fetch joins if needed. |
| Cache Inconsistency | Eviction failed for a specific subject after role change. | Ensure `PermissionCacheEvictService` handles Redis key pattern matching correctly for targeted subjects. |
| Permission Denied for PII | User lacks `igrp.users.pii.view` permission. | Assign the required PII permission to the user's role. |
| Empty roles array in `/api/users/me/roles` | Database schema missing `nic` and `phone_number` columns, or iGRP Studio JSON files not updated. | Run SQL migrations: `ALTER TABLE t_user ADD COLUMN nic VARCHAR(13); ALTER TABLE t_user ADD COLUMN phone_number VARCHAR(32);` and update `.igrpstudio` JSON files. |
| User not found in authentication | JWT `sub` claim doesn't match database `external_id` field. | Ensure user synchronization process correctly sets `external_id` to match JWT `sub` claim. |

---

## Critical Implementation Changes

### **Repository Method Replacement**

**Problem**: The original `findByExternalId()` method used lazy loading for `@ManyToMany` relationships, causing `LazyInitializationException` when accessing `user.getRoles()` or `r.getPermissions()` outside the Hibernate session.

**Solution**: Created comprehensive fetch join method:

```java
@Query("""
    select u from IGRPUserEntity u 
    left join fetch u.roles r
    left join fetch r.permissions p
    where u.externalId = :externalId and u.status != 'DELETED'
""")
Optional<IGRPUserEntity> findByExternalIdWithRolesAndPermissions(String externalId);
```

**Files Updated**: All handlers using `findByExternalId()` were systematically updated:
- `GetCurrentUserRolesQueryHandler`
- `GetCurrentUserPermissionsQueryHandler` 
- `GetActiveCurrentUserRoleQueryHandler`
- `SetActiveCurrentUserRoleCommandHandler`
- `PermissionCacheService`
- All other `GetCurrent...` handlers (7 total)

### **Why This Change Was Necessary**

The enterprise authorization system requires **nested relationship access**:
1. **User → Roles** (for T10/T11 multi-role functionality)
2. **Roles → Permissions** (for T12 permission aggregation)
3. **Authorization checks** (for PermissionCacheService)

Without fetch joins, these relationships would trigger lazy loading outside the transaction context, causing:
- Empty arrays in API responses
- `LazyInitializationException` in authorization checks
- Inconsistent behavior across different endpoints

This fix ensures **consistent, predictable loading** of all required relationship data in a single database query.
