# Session Management Module

This module implements comprehensive session management for the iGRP access management system.

## Architecture

### **Domain Layer**
- `SessionStatus` enum - Session states (ACTIVE, CLOSED, EXPIRED, REVOKED)
- `SessionEvent` hierarchy - Domain events for session lifecycle
- Session services - Core business logic

### **Infrastructure Layer**
- `SessionEntity` - JPA entity with audit support
- `SessionRepository` - Custom repository with optimized queries
- `SessionCacheEvictService` - Redis-based cache management
- `SessionCleanupScheduler` - Automated session cleanup

### **Application Layer**
- DTOs - Request/response objects for API
- Event listeners - Session lifecycle and security event handling
- Controllers - REST endpoints for users and administrators

## Key Features

### **Session Persistence**
- One active session per user enforcement at database level
- Comprehensive audit trail with created/modified tracking
- IP and device tracking for security monitoring
- User agent hashing for privacy protection

### **Performance & Caching**
- Redis-based session caching with 90%+ hit rate target
- Efficient bulk invalidation operations
- Optimized database queries with proper indexes

### **Security Features**
- JWT authentication integration
- Session fixation protection via rotation
- Immediate invalidation on role/permission changes
- Admin session management with proper authorization

### **API Endpoints**

#### User Endpoints (`/api/session/*`)
- `GET /api/session` - Get current session
- `POST /api/session/init` - Create new session
- `POST /api/session/refresh` - Extend session
- `POST /api/session/close` - Close session
- `POST /api/session/rotate` - Rotate session (fixation protection)

#### Admin Endpoints (`/api/admin/sessions/*`)
- `GET /api/admin/sessions` - List sessions with pagination
- `POST /api/admin/sessions/{sessionId}/kill` - Terminate specific session
- `POST /api/admin/sessions/roles/{roleCode}/kill` - Bulk invalidation by role
- `POST /api/admin/sessions/departments/{deptCode}/kill` - Bulk invalidation by department
- `GET /api/admin/sessions/statistics` - Session statistics

## Configuration

### **Properties**
All configuration is externalized via `application-session.properties`:

```properties
igrp.session.timeout-seconds=1800              # Session timeout (30 minutes)
igrp.session.cleanup.interval-seconds=300          # Cleanup interval (5 minutes)
igrp.session.old-session.retention-days=30          # Retention period (30 days)
igrp.session.max-extension-seconds=7200             # Max extension (2 hours)
igrp.session.min-extension-seconds=60              # Min extension (1 minute)
igrp.session.cache-enabled=true                       # Enable caching
igrp.session.cache-ttl-seconds=1800                   # Cache TTL
igrp.session.fixation-protection-enabled=true          # Fixation protection
igrp.session.ip-tracking-enabled=true                  # IP tracking
igrp.session.user-agent-hashing-enabled=true           # User agent hashing
```

### **Environment Variables**
- `IGRP_SESSION_TIMEOUT_SECONDS` - Override default session timeout

## Database Schema

### **Table: t_user_session**
```sql
CREATE TABLE t_user_session (
  id               BIGSERIAL PRIMARY KEY,
  session_id       UUID NOT NULL UNIQUE,
  user_external_id VARCHAR(255) NOT NULL,
  status           VARCHAR(16) NOT NULL,
  started_at       TIMESTAMPTZ NOT NULL,
  last_seen_at     TIMESTAMPTZ NOT NULL,
  expires_at       TIMESTAMPTZ NOT NULL,
  ended_at         TIMESTAMPTZ NULL,
  client_ip        INET NULL,
  user_agent_hash  VARCHAR(64) NULL,
  device_id        VARCHAR(128) NULL,
  closed_reason    VARCHAR(64) NULL,
  closed_by        VARCHAR(32) NULL,
  -- Audit fields inherited from AuditEntity
  created_date     TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by       VARCHAR(64) NOT NULL,
  last_modified_date TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_modified_by VARCHAR(64) NOT NULL
);
```

### **Indexes**
- `ix_session_user_status` - `(user_external_id, status)`
- `ix_session_expires_active` - `(expires_at) WHERE status = 'ACTIVE'`
- `ux_session_session_id` - UNIQUE on `session_id`
- `ux_one_active_session_per_user` - UNIQUE on `user_external_id` WHERE status = 'ACTIVE'

## Integration Points

### **Existing System Dependencies**
- `AuthenticationHelper.getSub()` - User identity extraction
- `IGRPUserEntity`, `RoleEntity`, `DepartmentEntity` - User profile mapping
- `PermissionCacheService` patterns - Cache implementation reference
- `SecurityAuditService` - Audit logging integration
- Domain event system - Role/permission change listeners

### **Event-Driven Invalidation**
- `UserRoleChangedEvent` - Immediate session invalidation on role changes
- `DeletePermissionEvent` - Permission deletion handling
- Session lifecycle events - Audit logging and monitoring

## Performance Characteristics

### **Cache Strategy**
- Key format: `sessionCache::{user_external_id}`
- Value: `SessionResponseDTO` with user profile and roles
- TTL: Matches session timeout (default: 30 minutes)
- Eviction: Session close, role changes, permission changes, timeout

### **Database Optimization**
- Efficient bulk operations for admin invalidation
- Proper indexing for common query patterns
- Scheduled cleanup of expired sessions
- Retention policy for old session data

## Security Considerations

### **Session Fixation Protection**
- Session rotation endpoint creates new session ID
- Automatic cleanup of old session on rotation
- IP and device tracking for anomaly detection

### **Privacy Protection**
- User agent hashing (SHA-256) prevents PII storage
- No raw tokens stored in database or logs
- Configurable IP tracking for GDPR compliance

### **Audit Trail**
- Full session lifecycle logging
- Closed/ended sessions with reasons
- Admin actions tracked with performer identification

## Deployment Notes

### **Migration**
Run the database migration: `V1_0_0__create_session_table.sql`

### **Configuration**
Include `application-session.properties` in your application configuration or set environment variables.

### **Dependencies**
Ensure Redis is available for caching functionality.
Ensure proper database permissions for session table operations.

## Monitoring & Operations

### **Health Checks**
- Session cleanup scheduler health endpoint
- Cache availability monitoring
- Database connection validation

### **Metrics**
- Session creation/termination rates
- Cache hit/miss ratios
- Expired session cleanup statistics
- Admin operation audit logs

This implementation follows the SKILL.md specification and integrates seamlessly with the existing iGRP access management architecture.
