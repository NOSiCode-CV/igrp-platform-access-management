# Session Management Module

Server-side session layer for the iGRP access-management OAuth2/OIDC stack.
Every JWT issued by the authorization server is bound to a `t_user_session`
row via the `sid` claim, so revoking a session takes effect immediately on
the next authenticated request — independent of the JWT's own `exp`.

> Companion design docs:
> [`_specs/session/requirements.md`](../../../../../../../../_specs/session/requirements.md),
> [`_specs/session/plan.md`](../../../../../../../../_specs/session/plan.md),
> [`_specs/session/validation.md`](../../../../../../../../_specs/session/validation.md).

---

## Architecture

```
                  +-----------------------------+
   /oauth2/token  |  Spring Authorization Server|
   ------------->|  + JwtTokenConfig customizer |------+
                  |  (issues access + refresh)  |      |
                  +-----------------------------+      v
                                                +-------------+
                                                | SessionEntity| <-- Redis SID
                                                | (sid, jti,   |     hot-cache
                                                |  device_id)  |
                                                +------+------+
                                                       |
                  +-----------------------------+      |
   protected API  | SessionEnforcementFilter    |      |
   ------------->| (after BearerTokenAuth)      |<-----+
                  | reads sid, hits Redis -> DB |
                  +-----------------------------+
                                                       ^
                                                       |
                  +-----------------------------+      |
   /oauth2/revoke | RevocationCascadeListener   |      |
   /connect/logout| (revokes Authorization +    |------+
                  |  SessionEntity)             |
                  +-----------------------------+
```

### Key design decisions

- **`sid` is the canonical session identifier**, equal to
  `SessionEntity.session_id`. Carried in every access token, used for all
  server-side lookups.
- **Concurrency**: N sessions per user, scoped by `(user_id, device_id)`.
  Two concurrent sessions cannot share a device. LRU eviction at the
  configured cap (default 5).
- **Refresh tokens rotate** (`reuseRefreshTokens=false`). The same `sid`
  is preserved across rotations until the session is killed or hits its
  absolute lifetime ceiling.
- **OAuth2 authorizations are persisted** (`JdbcOAuth2AuthorizationService`)
  so revocation survives restarts and we can join `OAuth2Authorization`
  with `SessionEntity`.
- **Tokens-not-valid-before floor (Phase F1)** lets a password reset /
  forced re-auth invalidate every JWT for a user with a single SQL update.

---

## Sequence diagrams

### Issuance (auth-code, password, federated grants)

```
Client      Auth Server              SessionIssuanceService    DB        Redis
  |              |                            |                  |         |
  | POST         |                            |                  |         |
  | /oauth2/token|                            |                  |         |
  |------------->|                            |                  |         |
  |              | igrpTokenCustomizer        |                  |         |
  |              | (ACCESS_TOKEN)             |                  |         |
  |              |--------------------------->|                  |         |
  |              |                            | resolve          |         |
  |              |                            | device_id        |         |
  |              |                            | (header or hash) |         |
  |              |                            |                  |         |
  |              |                            | close prior      |         |
  |              |                            | (user, device)   |         |
  |              |                            | ACTIVE row       |         |
  |              |                            |----------------->|         |
  |              |                            |                  |         |
  |              |                            | LRU evict if     |         |
  |              |                            | over cap         |         |
  |              |                            |----------------->|         |
  |              |                            |                  |         |
  |              |                            | INSERT new       |         |
  |              |                            | SessionEntity    |         |
  |              |                            | (sid, jti,       |         |
  |              |                            |  client_id,      |         |
  |              |                            |  exp, abs_exp)   |         |
  |              |                            |----------------->|         |
  |              |                            |                  |         |
  |              |                            | evict user-keyed |         |
  |              |                            | session cache    |         |
  |              |                            |------------------|-------->|
  |              |                            |                  |         |
  |              |  IssuanceBinding(sid,      |                  |         |
  |              |  device_id)                |                  |         |
  |              |<---------------------------|                  |         |
  |              |                            |                  |         |
  |              | add claims sid, device_id, |                  |         |
  |              | sign & return token        |                  |         |
  |<-------------|                            |                  |         |
  | { access_token, refresh_token }           |                  |         |
```

### Refresh

```
Client      Auth Server              SessionIssuanceService    DB        Redis
  |              |                            |                  |         |
  | POST         |                            |                  |         |
  | /oauth2/token|                            |                  |         |
  | grant=refresh|                            |                  |         |
  |------------->|                            |                  |         |
  |              | bindAccessToken            |                  |         |
  |              | (REFRESH_TOKEN)            |                  |         |
  |              |--------------------------->|                  |         |
  |              |                            | locate session   |         |
  |              |                            | by prior sid     |         |
  |              |                            | (or user,device) |         |
  |              |                            |----------------->|         |
  |              |                            |                  |         |
  |              |                            | reject if not    |         |
  |              |                            | ACTIVE / past    |         |
  |              |                            | absolute_expires |         |
  |              |                            | -> SessionRefresh|         |
  |              |                            | RejectedException|         |
  |              |                            | (invalid_grant)  |         |
  |              |                            |                  |         |
  |              |                            | else: slide      |         |
  |              |                            | expires_at,      |         |
  |              |                            | update jti,      |         |
  |              |                            | last_seen_at     |         |
  |              |                            |----------------->|         |
  |              |                            |                  |         |
  |              |                            | evict cache      |         |
  |              |                            |------------------|-------->|
  |              |  IssuanceBinding(same sid) |                  |         |
  |              |<---------------------------|                  |         |
  |              | sign & return new tokens   |                  |         |
  |<-------------|                            |                  |         |
```

### Enforcement (every protected request)

```
Client      ResourceServer    SessionEnforcementFilter   Redis     DB
  |              |                       |                |         |
  | GET /api/X   |                       |                |         |
  | Bearer JWT   |                       |                |         |
  |------------->|                       |                |         |
  |              | BearerTokenAuthFilter |                |         |
  |              | -> JwtAuthToken       |                |         |
  |              |---------------------->|                |         |
  |              |                       |                |         |
  |              |                       | sid claim?     |         |
  |              |                       | (if absent ->  |         |
  |              |                       |  pass through) |         |
  |              |                       |                |         |
  |              |                       | F1: load       |         |
  |              |                       | tokens_not_    |         |
  |              |                       | valid_before;  |         |
  |              |                       | reject if      |         |
  |              |                       | iat < floor    |         |
  |              |                       |--------------->|         |
  |              |                       |                |         |
  |              |                       | snapshot sid?  |         |
  |              |                       |--------------->|         |
  |              |                       |                |         |
  |              |                       |   miss         |         |
  |              |                       | findBySessionId|         |
  |              |                       |------------------------->|
  |              |                       |                |         |
  |              |                       | denialReason() |         |
  |              |                       | -> 401 (revoked|         |
  |              |                       | / expired)     |         |
  |              |                       | -> SC_SERVICE_ |         |
  |              |                       |    UNAVAILABLE |         |
  |              |                       |    on outage   |         |
  |              |                       |                |         |
  |              |                       | else: cache,   |         |
  |              |                       | touch debounced|         |
  |              |                       | last_seen_at   |         |
  |              |                       |--------------->|         |
  |              |                       |                |         |
  |              | continue chain        |                |         |
  |              |<----------------------|                |         |
  | 200 OK       |                       |                |         |
  |<-------------|                       |                |         |
```

### Revocation cascade

```
Caller              Auth Server         RevocationCascadeListener   DB        Redis
  |                    |                            |                  |         |
  | /oauth2/revoke or  |                            |                  |         |
  | /connect/logout    |                            |                  |         |
  |------------------->|                            |                  |         |
  |                    | OAuth2AuthorizationService |                  |         |
  |                    | .remove(authorization)     |                  |         |
  |                    |--------------------------->|                  |         |
  |                    |                            | resolve sid from |         |
  |                    |                            | access-token     |         |
  |                    |                            | claims           |         |
  |                    |                            |                  |         |
  |                    |                            | mark SessionEntity         |
  |                    |                            | REVOKED          |         |
  |                    |                            |----------------->|         |
  |                    |                            |                  |         |
  |                    |                            | evict SID hot    |         |
  |                    |                            | cache + user     |         |
  |                    |                            | -keyed cache     |         |
  |                    |                            |------------------|-------->|
  |                    |                            |                  |         |
  |                    |                            | publish          |         |
  |                    |                            | SessionRevokedEv |         |
  | 200 OK             |<---------------------------|                  |         |
  |<-------------------|                            |                  |         |
```

---

## Public surface

### User endpoints (`/api/session/*`)

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/session`         | Current session for the authenticated caller. |
| GET  | `/api/session/check`   | Combined JWT + server-side state. Bypasses the enforcement filter so a revoked-session JWT can still see `valid=false` with a reason. |
| POST | `/api/session/refresh` | Slide the current session's `expires_at`. |
| POST | `/api/session/rotate`  | Close current session and open a new one (fixation protection). |

### Admin endpoints (`/api/admin/sessions/*`, `/api/admin/users/{externalId}/...`)

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/admin/sessions`                                      | Paginated session list. |
| GET  | `/api/admin/sessions/users/{userId}`                       | Active session for a user. |
| POST | `/api/admin/sessions/{sessionId}/kill`                     | Kill one session. |
| GET  | `/api/admin/sessions/roles/{roleCode}`                     | List sessions for users in a role. |
| POST | `/api/admin/sessions/roles/{roleCode}/kill`                | Kill all sessions for a role. |
| POST | `/api/admin/sessions/departments/{deptCode}/kill`          | Kill all sessions in a department. |
| POST | `/api/admin/users/{externalId}/sessions/{sessionId}/kill`  | Kill one session, scoped to one user. |
| POST | `/api/admin/users/{externalId}/logout-all`                 | Revoke every active session for a user. |
| POST | `/api/admin/users/{externalId}/force-reauth`               | Phase F1 — bump `tokens_not_valid_before` and revoke sessions. |

### OAuth2/OIDC endpoints (Spring Authorization Server)

| Method | Path | Notes |
|---|---|---|
| POST | `/oauth2/token`         | Issues access + refresh tokens; binds a session row. |
| POST | `/oauth2/revoke`        | Cascades to `SessionEntity` via `RevocationCascadeListener`. |
| POST | `/oauth2/introspect`    | Returns `active=false` when the bound session is dead. |
| POST | `/connect/logout`       | OIDC end-session; revokes the authorization and session. |

---

## JWT claims

| Claim       | Source | Notes |
|-------------|--------|-------|
| `sub`       | Authorization principal | Internal user id. |
| `jti`       | Spring Auth Server      | Mirrored to `SessionEntity.jti`. |
| `iat`       | Spring Auth Server      | Compared to `tokens_not_valid_before` on every request (Phase F1). |
| `exp`       | Spring Auth Server      | Token-level lifetime. |
| `sid`       | `SessionIssuanceService` | UUID — canonical session id. |
| `device_id` | `SessionIssuanceService` | Client header `X-Device-Id` or a SHA-256 hash of `(User-Agent, IP, client_id)`. |
| `selectedRole`, `org`, `permissions`, `resource_access` | `ClaimsEnrichmentService` | Existing iGRP enrichment, unchanged. |

---

## Configuration (`application-session.properties`)

```properties
igrp.session.timeout-seconds=1800              # sliding inactivity TTL
igrp.session.absolute-timeout-seconds=28800    # 8h hard ceiling
igrp.session.max-per-user=5                    # LRU cap per user
igrp.session.heartbeat-debounce-seconds=30     # last_seen_at write debounce
igrp.session.cleanup.interval-seconds=300
igrp.session.old-session.retention-days=30
igrp.session.cache-enabled=true
igrp.session.cache-ttl-seconds=1800
igrp.session.enforcement-enabled=true          # kill-switch for SessionEnforcementFilter
```

Environment overrides:
- `IGRP_SESSION_TIMEOUT_SECONDS`
- `IGRP_SESSION_ABSOLUTE_TIMEOUT_SECONDS`
- `IGRP_SESSION_MAX_PER_USER`
- `IGRP_SESSION_HEARTBEAT_DEBOUNCE_SECONDS`

---

## Database schema

### `t_user_session`

```sql
CREATE TABLE t_user_session (
  id                   BIGSERIAL PRIMARY KEY,
  session_id           UUID NOT NULL UNIQUE,
  user_id              INTEGER NOT NULL,
  status               VARCHAR(16) NOT NULL,
  started_at           TIMESTAMPTZ NOT NULL,
  last_seen_at         TIMESTAMPTZ NOT NULL,
  expires_at           TIMESTAMPTZ NOT NULL,
  absolute_expires_at  TIMESTAMPTZ NULL,
  jti                  VARCHAR(64) NULL,
  client_id            VARCHAR(128) NULL,
  ended_at             TIMESTAMPTZ NULL,
  client_ip            VARCHAR(64) NULL,
  user_agent_hash      VARCHAR(64) NULL,
  device_id            VARCHAR(128) NULL,
  closed_reason        VARCHAR(64) NULL,
  closed_by            VARCHAR(32) NULL,
  -- audit columns inherited from AuditEntity
  created_date         TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_by           VARCHAR(64) NOT NULL,
  last_modified_date   TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_modified_by     VARCHAR(64) NOT NULL
);

CREATE INDEX        ix_session_user_status     ON t_user_session (user_id, status);
CREATE INDEX        ix_session_expires_active  ON t_user_session (expires_at);
CREATE INDEX        ix_session_user_device     ON t_user_session (user_id, device_id);
CREATE INDEX        ix_session_jti             ON t_user_session (jti);
CREATE UNIQUE INDEX ux_session_user_device_active
    ON t_user_session (user_id, device_id) WHERE status = 'ACTIVE';
```

### `t_user` (Phase F1)

```sql
ALTER TABLE t_user ADD COLUMN tokens_not_valid_before TIMESTAMPTZ;
```

### Spring Authorization Server JDBC tables

The first time `JdbcOAuth2AuthorizationService` is wired, `DatabaseMigrationRunner`
creates the standard schemas: `oauth2_authorization`, `oauth2_authorization_consent`,
`oauth2_registered_client`.

---

## Metrics (Phase F2)

Exposed by `SessionMetrics` via Micrometer; visible on `/actuator/prometheus`:

| Counter (Prometheus name)                    | Tags     | Bumped from |
|----------------------------------------------|----------|-------------|
| `igrp_session_created_total`                 | —        | `SessionIssuanceService.openNewSession` |
| `igrp_session_evicted_lru_total`             | —        | LRU eviction in `openNewSession` |
| `igrp_session_rejected_revoked_total`        | `reason` | `SessionEnforcementFilter` denials |
| `igrp_session_refresh_rejected_total`        | `reason` | `SessionIssuanceService.applyRefresh` rejection |
| `igrp_session_heartbeat_total`               | —        | Accepted authenticated requests |

Common values for `reason`:
- `session_revoked`, `session_expired`, `tokens_invalidated` (enforcement)
- `session_not_active`, `absolute_lifetime_exceeded` (refresh)

---

## Event-driven invalidation

| Event                          | Publisher | Listener effect |
|--------------------------------|-----------|-----------------|
| `UserRoleChangedEvent`         | role assignment / activation / invitation handlers | Invalidate sessions for the affected user. |
| `RolePermissionChangedEvent`   | role permission add/remove/update/delete handlers   | Invalidate sessions for users in that role. |
| `UserStatusChangedEvent`       | user status mutation sites                          | Invalidate sessions for the user. |
| `DeletePermissionEvent`        | permission delete handler                           | Invalidate sessions for users holding the permission. |
| `DepartmentScopeChangedEvent`  | department scope change handlers                    | Invalidate sessions for users in the department. |

---

## Security considerations

- **Session-bound JWTs**: revoking a session row terminates every in-flight
  JWT bound to it on the next request.
- **Tokens-not-valid-before** (Phase F1): a single SQL update invalidates
  every JWT for a user — even those issued microseconds ago — without
  needing to enumerate sessions.
- **Privacy**: `User-Agent` is SHA-256 hashed; raw tokens are never stored.
- **Failure mode**: on data-store outage the enforcement filter returns
  `503 Service Unavailable` instead of `401`, avoiding a forced-relogin
  storm during transient infrastructure failures.

---

## Operational notes

- Disable enforcement in an emergency with `igrp.session.enforcement-enabled=false`.
- The cleanup scheduler (`SessionCleanupScheduler`) marks expired ACTIVE rows
  EXPIRED and prunes rows older than `old-session.retention-days`.
- The Redis SID hot-cache (`igrp:session:bySid:{sid}`) has a 60 s TTL so a
  Redis flush self-heals within one cache window.
