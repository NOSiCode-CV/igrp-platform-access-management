# Session Fetching Test Guide

This guide validates the session management layer described in [SESSION_FETCHING.md](./docs/SESSION_FETCHING.md).

The goal is to prove that:
- The session lifecycle (init, fetch, refresh, close) works correctly via HTTP.
- Session persistence and status transitions (ACTIVE, CLOSED, EXPIRED, REVOKED) are correctly handled.
- The session cache (Redis) is correctly populated and invalidated.
- Administrative session controls (list, kill, bulk kill) function as expected.
- Automatic timeout and cleanup mechanisms are functional.

---

## Scope

This guide covers all session-related interactions via the API:

| Area | Covered here |
| --- | --- |
| User self-service session lifecycle | Yes |
| Session-active role and department resolution | Yes |
| Session persistence in `t_user_session` | Yes |
| Session cache population and eviction | Yes |
| Admin session listing and filtering | Yes |
| Session revocation (individual and bulk) | Yes |
| Invalidation on role/permission changes | Yes |
| Timeout enforcement | Yes |

---

## Source Coverage

This guide was derived from the [SESSION_FETCHING.md](./docs/SESSION_FETCHING.md) skill reference and the following core components:

- `cv.igrp.platform.access_management.shared.security.AuthenticationHelper`
- `cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity`
- `cv.igrp.platform.access_management.authorization.domain.service.PermissionCacheService`
- `cv.igrp.platform.access_management.shared.config.CacheConfig`
- `cv.igrp.platform.access_management.shared.infrastructure.cache.PermissionCacheEvictService`

---

## Test Strategy

Use **HTTP interaction validation** as the primary layer, complemented by database and cache checks:

1. **Self-Service API Validation**
   - Test the `init`, `fetch`, `refresh`, and `close` endpoints using valid JWT tokens.

2. **Admin API Validation**
   - Test the administrative endpoints for monitoring and killing sessions.

3. **Lifecycle & State Validation**
   - Verify that session statuses in the database correctly transition between `ACTIVE`, `CLOSED`, `EXPIRED`, and `REVOKED`.

4. **Cache & Invalidation Validation**
   - Verify that Redis cache entries are created upon session initialization and correctly evicted upon closure, timeout, or administrative revocation.

5. **Cross-Action Invalidation Validation**
   - Verify that changes to user roles or permissions correctly trigger session revocation for the affected users.

---

## Prerequisites

Set these variables before running the requests:

```bash
BASE_URL=http://localhost:8081
USER_TOKEN=<valid user bearer token>
ADMIN_TOKEN=<bearer token with session management permissions>
USER_SUB=<user_external_id / sub from the token>
ROLE_CODE=ADMIN
DEPARTMENT_CODE=HR
```

---

## Test Scenarios

### 1. User Session Lifecycle (Self-Service)

#### 1.1 Initialize Session
- **Request**: `POST /api/session/init`
- **Headers**: `Authorization: Bearer ${USER_TOKEN}`
- **Expected**: 
  - `201 Created` or `200 OK`
  - Response contains `sessionId` and `status: ACTIVE`.
  - Database: A new row exists in `t_user_session` with `status='ACTIVE'`.
  - Cache: Redis contains a key for `sessionCache::${USER_SUB}`.

#### 1.2 Fetch Current Session
- **Request**: `GET /api/session`
- **Headers**: `Authorization: Bearer ${USER_TOKEN}`
- **Expected**:
  - `200 OK`
  - Response includes `userProfile`, `currentActiveRole`, `roles`, and `departments`.
  - `roles` and `departments` match the user's database configuration.

#### 1.3 Refresh Session (Keep-Alive)
- **Request**: `POST /api/session/refresh`
- **Headers**: `Authorization: Bearer ${USER_TOKEN}`
- **Expected**:
  - `200 OK`
  - Database: `expires_at` is updated (extended by timeout period).
  - Cache: Updated with the new expiry.

#### 1.4 Close Session
- **Request**: `POST /api/session/close`
- **Headers**: `Authorization: Bearer ${USER_TOKEN}`
- **Expected**:
  - `204 No Content`
  - Database: Session status changed to `CLOSED`, `ended_at` is set.
  - Cache: `sessionCache::${USER_SUB}` is evicted.

---

### 2. Administrative Controls

#### 2.1 List All Active Sessions
- **Request**: `GET /api/admin/sessions?status=ACTIVE`
- **Headers**: `Authorization: Bearer ${ADMIN_TOKEN}`
- **Expected**:
  - `200 OK`
  - Returns a list of active sessions.

#### 2.2 Filter Sessions by Role/Department
- **Request**: `GET /api/admin/sessions/roles/${ROLE_CODE}?departmentCode=${DEPARTMENT_CODE}`
- **Headers**: `Authorization: Bearer ${ADMIN_TOKEN}`
- **Expected**:
  - `200 OK`
  - Returns only sessions for users belonging to the specified role/department.

#### 2.3 Kill a Specific Session
- **Request**: `POST /api/admin/sessions/${SESSION_ID}/kill`
- **Headers**: `Authorization: Bearer ${ADMIN_TOKEN}`
- **Expected**:
  - `204 No Content`
  - Database: Session status changed to `REVOKED`.
  - Cache: Evicted for the affected user.

#### 2.4 Bulk Kill by Role
- **Request**: `POST /api/admin/sessions/roles/${ROLE_CODE}/kill?departmentCode=${DEPARTMENT_CODE}`
- **Headers**: `Authorization: Bearer ${ADMIN_TOKEN}`
- **Expected**:
  - `204 No Content`
  - All sessions for users with that role are marked `REVOKED` in DB and evicted from cache.

---

### 3. Automatic Timeout & Cleanup

#### 3.1 Read-Time Timeout Enforcement
- **Action**: Manually set `expires_at` to a past date in the database for an active session.
- **Request**: `GET /api/session`
- **Headers**: `Authorization: Bearer ${USER_TOKEN}`
- **Expected**:
  - `204 No Content` (or `200 OK` with `active=false`)
  - Database: Session status is updated to `EXPIRED`.
  - Cache: Key is evicted.

#### 3.2 Scheduled Cleanup
- **Action**: Wait for the scheduled task execution interval.
- **Expected**:
  - Database: All sessions with `expires_at < now()` and `status='ACTIVE'` are updated to `EXPIRED`.
  - Cache: Keys for expired sessions are evicted.

---

### 4. Invalidation on Configuration Changes

#### 4.1 Invalidate on Role Membership Change
- **Action**: Add or remove a role from a user.
- **Verification**: Check if the user's active session is invalidated.
- **Expected**:
  - Database: Session marked `REVOKED`.
  - Cache: `sessionCache::${USER_SUB}` is evicted.

#### 4.2 Invalidate on Role Permission Change (Bulk)
- **Action**: Change permissions for a role (add/remove).
- **Verification**: Check sessions of all users assigned to that role.
- **Expected**:
  - Database: All active sessions for those users are marked `REVOKED`.
  - Cache: All relevant user keys are evicted.

---

## Acceptance Checklist

- [ ] All lifecycle endpoints (init, fetch, refresh, close) return correct HTTP statuses.
- [ ] Database `t_user_session` reflects session state changes accurately.
- [ ] Redis `sessionCache` keys are correctly managed (created/updated/evicted).
- [ ] Role and department resolution in `GET /api/session` is consistent with user roles.
- [ ] Administrative `kill` and `bulk kill` actions correctly revoke sessions.
- [ ] Invalidation logic triggers immediately upon role or permission changes.
- [ ] Timeout enforcement works both at read-time and via the scheduled cleanup.
- [ ] Unauthorized calls to session endpoints are rejected with `401` or `403`.
