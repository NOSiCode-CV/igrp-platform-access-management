---
name: "session-fetching"
description: "Implements session layer with persistence, caching, and API endpoints. Invoke when implementing user session management, admin session control, or session-based authorization optimization."
---

# Session Fetching — Implementation Guide

Reference document: [SESSION_FETCHING.md](../../../../docs/SESSION_FETCHING.md)

---

## Scope

**In scope:**
- **Session Persistence**: Create `SessionEntity` mapping to `t_user_session` table with one active session per user enforcement.
- **Session Cache Layer**: Implement `SessionCacheService` and `SessionCacheEvictService` mirroring existing permission cache patterns.
- **Session API Endpoints**: Create user session endpoints (`/api/session/*`) for self-service session management.
- **Admin Session Management**: Implement admin endpoints (`/api/admin/sessions/*`) for session monitoring and control.
- **Session Invalidation Service**: Create event-driven invalidation on role/permission changes with bulk operations.
- **Session Cleanup Scheduler**: Implement scheduled cleanup of expired sessions with cache eviction.
- **Security Integration**: Add IP tracking, user-agent hashing, audit logging, and session fixation protection.

---

## Implementation Architecture

### Database Layer
```java
// SessionEntity with JPA annotations
// SessionRepository with custom queries
// Database migration for t_user_session table
```

### Cache Layer  
```java
// SessionCacheService with @Cacheable
// SessionCacheEvictService with Redis scanning
// SessionCacheKeyGenerator following permission cache pattern
```

### Service Layer
```java
// SessionManagementService for core operations
// SessionInvalidationService for event-driven cleanup
// Integration with existing AuthenticationHelper
```

### API Layer
```java
// SessionController for user endpoints
// AdminSessionController for administrative operations
// Security annotations and validation
```

---

## Key Implementation Details

### Session Entity Requirements
- UUID session_id as unique identifier
- user_external_id linking to AuthenticationHelper.getSub()
- Status enum: ACTIVE, CLOSED, EXPIRED, REVOKED
- Audit fields: created_date, last_modified_date
- Security fields: client_ip, user_agent_hash, device_id
- Enforce one ACTIVE session per user via partial unique index

### Cache Integration Pattern
- Cache name: `sessionCache`
- Key format: `sessionCache::{user_external_id}`
- Value: `SessionResponseDTO` with user profile and roles
- Eviction on: session close, role changes, permission changes, timeout
- Redis scan prefix: `sessionCache::`

### API Endpoint Specifications
- `GET /api/session` - Return current session or 204
- `POST /api/session/init` - Create new session, close existing
- `POST /api/session/refresh` - Extend session expiry
- `POST /api/session/close` - Close current session
- `POST /api/session/rotate` - Prevent session fixation
- `GET /api/admin/sessions` - List with pagination and filters
- `POST /api/admin/sessions/{sessionId}/kill` - Admin termination
- `POST /api/admin/sessions/roles/{roleCode}/kill` - Bulk invalidation

### Event-Driven Invalidation
- Listen to: RoleChangedEvent, PermissionsChangedEvent, UserRoleChangedEvent
- Immediate invalidation on security-relevant changes
- Bulk operations by role or department
- Integration with existing domain event system

### Security Requirements
- JWT authentication required for all endpoints
- Admin endpoints need SESSION_MANAGEMENT permission
- No raw tokens stored in database
- IP and user-agent tracking for anomaly detection
- Integration with SecurityAuditService
- Session fixation protection via rotation

---

## Integration Points

### Existing System Dependencies
- `AuthenticationHelper.getSub()` for user identity
- `IGRPUserEntity`, `RoleEntity`, `DepartmentEntity` for relationships
- `PermissionCacheService` patterns for cache implementation
- `SecurityAuditService` for audit logging
- Existing domain event system for invalidation triggers

### Configuration Requirements
```properties
igrp.session.timeout-seconds=${IGRP_SESSION_TIMEOUT_SECONDS:1800}
```

---

## Implementation Order

1. **Database Schema**: Create t_user_session table with indexes
2. **Entity & Repository**: SessionEntity and SessionRepository
3. **Cache Layer**: SessionCacheService and eviction service
4. **Core Services**: SessionManagementService and invalidation service
5. **API Controllers**: User and admin session endpoints
6. **Event Integration**: Add listeners to existing command handlers
7. **Scheduled Tasks**: Session cleanup scheduler
8. **Security Integration**: Authentication, authorization, audit logging

---

## Validation Criteria

- One active session per user enforced in database
- Session cache hit rate >90% under normal load
- All API endpoints functional with proper authentication
- Immediate invalidation on role/permission changes (<1 second)
- Admin session management operational
- Security audit logging captures all session operations
- No raw tokens stored in logs or database
- Session fixation protection effective
- Automatic cleanup of expired sessions
- Performance benchmarks met under load
