# Feature Specification: Multi-Provider IAM Synchronization API

---
## Revision

| Version | Author            | Date       | Changes                     |
|---------|-------------------|------------|-----------------------------|
| 1.0.0   | @Marcelo.Monteiro | 2025-09-16 | Page detection alternatives |
| ...     | ...               | ...        | ...                         |

---

# Table of Contents
1. Overview
2. Goals & Non-Goals
3. Requirements
    - Functional Requirements
    - Non-functional Requirements
4. Constraints & Assumptions
5. Concepts & Definitions
6. High-Level Architecture
7. Data Model & Schemas
    - Entity Models
    - Provider Mapping
    - Outbox & Sync Audit
    - SQL Migration Examples
8. Adapter Contract (`IAdapter`) — Detailed Interface
    - Capabilities & Semantics
    - Error Model & Retries
9. Sync Patterns & Algorithms
    - Event-driven sync (preferred)
    - Startup reconciliation (mandatory)
    - Delta fetches and snapshot comparison
    - Bulk, batched and parallel operations
    - Conflict detection & resolution policies
10. Idempotency: concepts & implementation
11. Soft deletes & lifecycle management
12. Federated/social login flows (Google example)
13. Mapping & transformation rules
14. Security considerations
15. Observability & Monitoring
16. Operational runbook
17. Testing strategy and test cases (unit, integration, e2e, performance)
18. Migration & rollout plan
19. Appendix
    - Sequence diagrams (ASCII)
    
---

# 1. Overview

This specification details a robust synchronization system to keep one or more IAM providers (Keycloak, WSO2, and social providers surfaced through a primary IAM like Keycloak) consistent with a central Business Logic Database (DB). The DB is the canonical source of business data (users, roles, permissions, departments, applications, resources). Providers are authoritative for provider-specific attributes (password hashes, provider-managed MFA, social federations) but must converge with DB for business configuration.

The sync system must:
- Operate bidirectionally (DB ↔ provider).
- Run a verification and reconciliation at API startup and on-demand.
- Be performant and scalable for large user bases.
- Be idempotent, resilient to retries and partial failures.
- Provide audit trails and reconciliation reports.

---

# 2. Goals & Non-Goals

## Goals
- Ensure eventual consistency with startup verification to speed up convergence.
- Minimize a provider load using delta checks and batched updates.
- Provide clear conflict resolution policies.
- Expose on-demand APIs for immediate checks and repairs.
- Maintain full auditability using Envers + sync-specific audit tables.

## Non-Goals
- Provide real-time strict consistency across globally distributed databases (we allow eventual consistency).
- Replace provider-native authentication flows or features (e.g., provider-managed MFA remains provider-owned).
- Implement UI in this specification — operational APIs and data models only.

---

# 3. Requirements

## Functional Requirements
1. Bidirectional synchronization between DB and all registered providers.
2. Startup check: on API boot, verify DB vs. provider(s) and repair differences.
3. On-demand API endpoints: `/sync/check`, `/sync/repair`, `/sync/status`.
4. Support multiple provider adapters implementing a shared `IAdapter`.
5. All DB entity changes persisted together with an outbox event for reliable delivery.
6. Provider-originated events must be consumable and able to create or update DB records (e.g., social user created).
7. Mapping table(s) to track provider object ids, attributes-hash, provider_updated_at, last_synced_change_number.
8. Soft-delete semantics for lifecycle management.
9. Audit logs for all sync operations, including before/after snapshots.
10. Configurable conflict resolution per-entity type.

## Non-functional Requirements
1. Scalable to millions of users and hundreds of thousands of roles/resources.
2. Low latency applies for per-request synchronous operations (admin flows).
3. Bounded background reconciliation works with checkpointing for crash recovery.
4. Resilient to provider rate-limiting and partial outages.
5. Secure: credentials encrypted, the least privilege, audit trails.
6. Observability: metrics, logs, reconciliation reports.

---

# 4. Constraints & Assumptions
- Providers expose stable admin APIs to list, create, update, and delete objects.
- Providers may support bulk operations (optimal) but may also impose rate-limits.
- Providers may or may not support an item-level `updated_at` timestamp; where absent, use attributes hash.
- The DB supports reliable transactions, triggers, and scheduled background jobs.
- Spring Boot stack with JPA/Hibernate in the codebase.
- The system can add new DB columns and tables and run migrations.

---

# 5. Concepts & Definitions

- **SoT**: Source of Truth (the DB).
- **Adapter**: Provider-specific implementation of the `IAdapter` contract.
- **Outbox**: An append-only table storing events generated by DB transactions for asynchronous processing.
- **Provider Mapping**: Table mapping DB entities to provider object ids and metadata.
- **sync_version / change_number**: Monotonic integer increasing at every change to an entity in DB.
- **attributes_hash**: A compact fingerprint of key entity attributes used to compare objects cheaply.
- **Delta Fetch**: Fetching only objects changed since a known point in time/version.
- **Reconciliation**: Process of comparing a DB snapshot to a provider snapshot and repairing differences.

---

# 6. High-Level Architecture

```
+---------------------+           +------------------+          +---------------------+
| Business DB (SoT)   | <-outbox->| Sync Core Worker | <-API->  | Admin / Sync API    |
|   (JPA + Envers)    |           |                 |          | (/sync/check,...)   |
+----------+----------+           +---------+-------+          +---------+-----------+
           |                                |                            |
           |                                v                            |
           |                          +-----+------+                     |
           |                          |Adapters (IAdapter)--------------> Providers
           |                          +-----+------+                     |
           |                                |                            |
           +--------------------------------+----------------------------+
```

Components:
- **Business DB**: Entities, `provider_mapping`, `outbox`, `sync_audit`.
- **Sync Core**:
    - Outbox processor(s): consume events, call adapters.
    - Reconciler: startup and periodic reconciliation calls adapters and uses snapshots.
    - Conflict resolver: per-policy actions.
- **Adapters**: KeycloakAdapter, WSO2Adapter, etc.
- **Admin API**: allows manual checks, repairs, and reports.

---

# 7. Data Model & Schemas

## 7.1 Core Entities (suggested)
Tables: `t_user`, `role`, `permission`, `department`, `application`, `resource`

Example `t_user` table:
```sql
CREATE TABLE t_user (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username TEXT UNIQUE,
  email TEXT,
  email_verified BOOLEAN DEFAULT FALSE,
  full_name TEXT,
  status TEXT DEFAULT 'ACTIVE', -- ENUM recommended
  deleted_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  change_number BIGINT NOT NULL DEFAULT 0 -- sync_version
);
```

Example `role` table:
```sql
CREATE TABLE role (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code TEXT UNIQUE, -- canonical business code, e.g. ROLE_ADMIN
  display_name TEXT,
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  change_number BIGINT NOT NULL DEFAULT 0
);
```

Keep similar model for `permission`, `department` (with `code`), `application`, `resource`. Use `code` as canonical business identifier for entities that are referenced externally (departments, roles).

## 7.2 Provider Mapping Table

One central mapping table to map any entity to provider object ids. This lets us support multiple providers and multiple mappings per entity.

```sql
CREATE TABLE provider_mapping (
  id BIGSERIAL PRIMARY KEY,
  entity_type TEXT NOT NULL, -- 'USER','ROLE','DEPARTMENT','PERMISSION','APP','RESOURCE'
  entity_id UUID NOT NULL,
  provider_name TEXT NOT NULL, -- 'keycloak','wso2','google'
  provider_id TEXT NOT NULL,   -- provider object id
  provider_name_display TEXT NULL,
  provider_attributes_hash TEXT NULL,
  provider_updated_at TIMESTAMPTZ NULL,
  last_synced_at TIMESTAMPTZ NULL,
  last_synced_change_number BIGINT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (provider_name, provider_id),
  UNIQUE (entity_type, entity_id, provider_name)
);
```

Notes:
- `provider_attributes_hash` is a hex or base64 string representing a hash (SHA-256) of canonical attributes.
- `last_synced_change_number` is the DB `change_number` value last applied to the provider for that entity.

## 7.3 Outbox Table (Reliable Delivery)

```sql
CREATE TABLE outbox (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  aggregate_type TEXT NOT NULL, -- e.g., 'USER','ROLE'
  aggregate_id UUID NOT NULL,
  change_number BIGINT NOT NULL,
  event_type TEXT NOT NULL, -- e.g., 'USER_CREATED','ROLE_UPDATED'
  payload JSONB NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, PROCESSING, DONE, FAILED
  attempt_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_outbox_status ON outbox(status);
```

Outbox semantics:
- The outbox row is created inside the same DB transaction as business changes.
- An outbox processor polls or streams pending rows, applies adapter operations, and updates status.

## 7.4 Sync Audit Table (Reconciliation / Conflict Logs)

```sql
CREATE TABLE sync_audit (
  id BIGSERIAL PRIMARY KEY,
  entity_type TEXT NOT NULL,
  entity_id UUID NULL,
  provider_name TEXT NULL,
  provider_id TEXT NULL,
  operation TEXT NOT NULL, -- 'PUSH','PULL','CONFLICT_RESOLVED','DELETE'
  payload JSONB NULL,
  before_state JSONB NULL,
  after_state JSONB NULL,
  result TEXT NOT NULL, -- 'SUCCESS','FAILED','SKIPPED'
  created_by TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

This stores per-operation audit info and is the key table for reporting.

## 7.5 Reconciliation Task Table

```sql
CREATE TABLE recon_task (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_name TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, DONE, FAILED
  page INT DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT now(),
  updated_at TIMESTAMPTZ DEFAULT now(),
  details JSONB NULL
);
```

Use recon_task for checkpointing long reconciliation runs.

---

# 8. Adapter Contract (`IAdapter`) — Detailed Interface

Adapters must encapsulate provider-specific semantics and expose a uniform contract. Use Java interface signatures as examples.

```java
public interface IAdapter {

    /**
     * Provider identifier (e.g., "keycloak")
     */
    String getProviderName();

    /**
     * Capabilities exposed by this adapter (bulk, search_by_updated_since, webhook support)
     */
    AdapterCapabilities getCapabilities();

    /**
     * Health check for provider connectivity check.
     */
    AdapterHealth checkHealth();

    // keep the other methods the same
    
    // --- Bulk operations (optional) ---
    BulkOpResult bulkUpsertUsers(List<UserIdentity> users) throws IAMException;
    BulkOpResult bulkUpsertRoles(List<String> roles) throws IAMException;

    // --- Snapshot helper ---
    ProviderSnapshot getSnapshot(EntityType type, int page, int pageSize);
    
}
```

### 8.1 AdapterCapabilities
- `supportsBulkUpsert`
- `supportsSnapshotByUpdatedSince`
- `supportsProviderAttributesHash`
- `supportsWebhooks`
- `maxBatchSize`

Adapters must:
- Normalize user provider responses into `UserIdentity`.
- Implement retries with exponential backoff and respect rate limits.
- Provide deterministic hashing for attributes (the same canonical attributes must produce identical hash across adapter and DB generation logic).

### 8.2 Error Model
- Throw `IAMException` containing:
    - `errorCode` (e.g., RATE_LIMIT, AUTH_FAIL, NOT_FOUND)
    - `retryable` boolean
    - `details`
- Sync core uses `retryable` to decide requeue/backoff.

---

# 9. Sync Patterns & Algorithms

This section defines the algorithms for efficient, resilient synchronization.

## 9.1 Event-driven Sync (Recommended)
- When an entity in DB is created/updated/deleted:
    - In the same DB transaction:
        - increment `change_number` (sync_version)
        - create outbox event with payload and `change_number`
    - Outbox processor picks event and calls adapter upsert/delete for relevant providers.
- Advantages:
    - Low-latency propagation.
    - Fine-grained failure handling per change.
    - Scales well with an incremental load.

Implementation details:
- Outbox processor runs as a separate Spring Boot worker (or as part of the main app with a dedicated thread pool).
- Use database polling or logical decoding (Debezium) to stream outbox events to worker.
- Worker applies idempotent upsert logic using `aggregate_id` and `change_number` to prevent duplicate application.

## 9.2 Startup Reconciliation (Mandatory)
- On API start (or periodic schedule), run the reconciler:
    - For each provider:
        - For each `EntityType` in canonical order (roles/permissions → departments → applications and resources → users → assignments):
            - Pull provider snapshot pages (provider id, attributes_hash, provider_updated_at).
            - Pull DB snapshot pages (entity id, canonical attributes hash, change_number, provider_mapping).
            - Compute diffs:
                - `OnlyInDB` → push to provider (bulk upsert where possible).
                - `OnlyInProvider` → import to DB (if allowed) or disable a provider object.
                - `Different` → resolve per conflict policy.
            - Update `provider_mapping` and `sync_audit`.
- Use `recon_task` checkpointing for long runs.

### 9.2.1 Snapshot Comparison Algorithm
- For each page `p`:
    1. Load `ProviderSnapshot` page `p` (list of `provider_id, attributes_hash, provider_updated_at`).
    2. For each provider record, attempt to find `provider_mapping` by `(provider_name, provider_id)`.
        - If mapping found, compare `mapping.provider_attributes_hash` vs `provider.attributes_hash`.
            - If different, pull the full object and mark for diff resolution.
        - If mapping is not found, treat it as provider-orphan.
    3. Load DB snapshot page for the same logical window (if you can align via `code` or `external_id` use that to correlate; otherwise cross-match by produced hash or provider attributes).
- Differences split into:
    - `DBOnly`, `ProviderOnly`, `BothButDifferent`.

### 9.2.2 Ordering & Dependencies
- Always sync definitions before assignments:
    - Roles & Permissions → Departments/Groups → Applications/Resources → Role→Permission assignments → User creation → User role/group assignments
- This avoids transient failures when creating users with roles referencing missing roles.

## 9.3 Delta Fetches & Efficient Checks
- Use `provider_updated_at` and `provider_attributes_hash` to fetch only changed provider objects (if provider supports).
- Use `db.change_number > mapping.last_synced_change_number` to find DB-changed objects.
- Maintain indexes on `change_number` and `last_synced_change_number` for fast queries.

## 9.4 Bulk & Batching Strategy
- Use provider bulk APIs where possible (Keycloak supports admin API batch operations in some forms).
- Determine `maxBatchSize` from `AdapterCapabilities`.
- Example: process users in batches of 200 (configurable per provider).
- Use concurrency controls: a bounded thread pool per provider with queue to respect rate limits.

## 9.5 Conflict Detection & Resolution
- Detect conflict when both DB and provider have changed since `mapping.last_synced_change_number` / `last_synced_at`.
- Decisions:
    - `DB_WINS` — push DB changes to provider.
    - `PROVIDER_WINS` — import provider changes into DB (create new revision).
    - `MANUAL_REVIEW` — create a `recon_task` for human review with diff in `sync_audit`.
- Provide per-entity-type configuration. Example configuration (YAML):

```yaml
sync:
  policy:
    user: DB_WINS
    role: DB_WINS
    permission: DB_WINS
    department: DB_WINS
    federated_user: PROVIDER_WINS
```

### 9.5.1 Conflict Resolution Steps
1. Compute `deltaDB = db.change_number - mapping.last_synced_change_number`
2. Compute `deltaProvider = providerUpdatedAt - mapping.last_synced_at` (or providerVersion)
3. If both deltas > 0 → conflict
4. Resolve per policy:
    - `DB_WINS`:
        - Push DB attributes to provider; increment `last_synced_change_number`.
        - Log to `sync_audit`.
    - `PROVIDER_WINS`:
        - Apply provider attributes to DB with a new `change_number` (transaction), create outbox if required to push to other providers.
        - Log to `sync_audit`.
    - `MANUAL_REVIEW`:
        - Create `recon_task` and a human-readable diff.

---

# 10. Idempotency

## 10.1 Definition
Idempotency ensures repeating the same operation multiple times leads to the same final state.

## 10.2 Mechanisms
- **Deterministic identifiers**: Use DB `id` as `external_id` in providers where possible.
- **Idempotency Keys**: Each outbox event uses `change_id` (UUID) as an idempotency key. Store results of applied keys in `sync_audit` or a separate idempotency table.
- **Upsert semantics**: Adapters implement upsert: check by `provider_id` or `external_id` and update or create accordingly.
- **Unique constraints**: Provider objects should use unique attributes (username/email) to prevent duplicates.

## 10.3 Example Upsert Flow (User)
1. Outbox event `change_id` with `user.id`, `change_number`.
2. Adapter checks `provider_mapping`:
    - If exists, call `update(provider_id, payload)`.
    - Else, call `findByExternalId(user.id)`:
        - If found, create mapping and update.
        - Else, create with `external_id=user.id`.
3. Mark outbox DONE and write entry to `sync_audit`.

---

# 11. Soft Delete & Lifecycle Management

## 11.1 Soft Delete Semantics
- Entities in DB are "deleted" by setting `deleted_at` and `status`.
- On delete:
    - Increment `change_number`.
    - Insert outbox `DELETE` event.
    - Adapters mark a provider object as disabled / set attribute `deleted=true` if supported.
- Hard delete occurs only after a retention period and manual/automated purge job; before hard delete, reconcile.

## 11.2 Retention Policy
- Default: 30 days retention for soft-deleted entities.
- During retention, an object is disabled in providers but retained for audit/restore.

## 11.3 Undo Delete
- Use Envers to roll back or admin UI to undelete: Undelete increments `change_number` and outbox re-creates/re-enables provider objects.

---

# 12. Federated / Social Login Flow (Google Example)

This section explains the canonical flow and edge cases.

## 12.1 Goals
- When a user logs in via Google (federated through Keycloak or direct), ensure a DB user exists and is linked to the provider.
- Avoid unwanted account merging or takeover.

## 12.2 Data to store on mapping
- `provider` (google)
- `provider_id` (Google `sub` or Keycloak federation link)
- `email`, `email_verified` (copy)
- `provider_attributes_hash`
- `linked_at`, `linked_method` (automatic/manual)

## 12.3 Flow (detailed)
1. User completes OAuth with Google; provider (Keycloak or your API) receives identity with `provider_id` (Google sub) and claims.
2. Adapter receives the provider event or the application receives a callback:
    - Extract `provider`, `external_id`, `email`, `email_verified`, `full_name`.
3. Resolution steps:
    - If `provider_mapping` exists for `(provider, provider_id)` → authenticate as mapped `t_user`.
    - Else if `email_verified == true`:
        - Search DB for `t_user` where `email = claim.email`.
        - If found:
            - **Link** mapping row to existing `t_user`. Record `linked_at`.
        - If not found:
            - **Create** new `t_user` with `source='federated'`, `username` generated (`google_<provider_id>`), `email` and `email_verified`.
            - Create `provider_mapping`.
    - Else (email not verified):
        - Create new `t_user` with `verified=false` and require verification via an email link before linking (higher security).
4. Assign default roles as defined by mapping rules or org policies. Emit outbox `USER_CREATED` or `USER_LINKED`.

## 12.4 Account Merge & Collision Handling
- If a provider `external_id` appears with email matching an existing `t_user` but `email_verified=false`, do **not auto-merge**. Instead, request user confirmation via email.
- Provide admin endpoints to manually link accounts, with audit trail.

## 12.5 Security considerations for federated creation
- Treat `email_verified` as authoritative only when coming from trusted provider and validated by Keycloak (i.e., Keycloak's `email_verified` is reliable).
- Avoid auto-linking if the risk of takeover exists (email is not unique, temporary emails, etc.).
- Keep an approval flow for sensitive roles assignment post-creation.

---

# 13. Mapping & Transformation Rules

Different providers have different models. Provide a mapping layer to transform a canonical DB model to provider-specific constructs.

## 13.1 Examples
- Canonical `Role` → Keycloak realm role or client role depending on `role.scope`.
- Canonical `Department` (code `DEPT_GPT`) → Keycloak group with name `DEPT_GPT` and attribute `business_code=DEPT_GPT`.
- Canonical `Permission` → provider-specific permission or scope. If the provider lacks fine-grained permissions, emulate using roles.

## 13.2 Mapping Configuration
A YAML or properties-based mapping config per adapter:

```yaml
adapter:
  keycloak:
    role:
      realm: true
      clientDefault: "my-client"
    department:
      createGroup: true
      groupNameTemplate: "{code}"
```

```properties
adapter.keycloak.role.realm=true
adapter.keycloak.role.clientDefault=my-client
adapter.keycloak.department.createGroup=true
adapter.keycloak.department.groupNameTemplate={code}
```

Adapters read mapping to generate correct provider payloads.

---

# 14. Security Considerations

## 14.1 Secrets & Credentials
- Store provider credentials in Vault (or equivalent); do not store in plaintext.
- Rotate service account keys regularly.
- Limit scopes to the least privilege (admin user dedicated to sync).

## 14.2 Data Protection
- Encrypt PII at rest if required.
- Hash attributes used for `provider_attributes_hash` without exposing sensitive fields.

## 14.3 Access Controls
- Only admin roles can trigger full reconciliation or override conflicts.
- Audit trails for manual operations must capture operator identity and reason.

## 14.4 Rate Limiting & Quotas
- Respect provider rate-limits and provide circuit-breaker patterns.
- Use exponential backoff for retryable errors.

---

# 15. Observability & Monitoring

## 15.1 Metrics
- `sync.outbox.pending_count`
- `sync.outbox.processed_rate`
- `sync.reconciler.duration`
- `sync.adapter.errors.total{provider}`
- `sync.conflicts.count`
- `sync.lag.{entity_type}` (max `change_number - last_synced_change_number`)

## 15.2 Logs
- Structured logs in JSON for sync operations.
- Include `change_id`, `entity_id`, `provider`, `operation`, `result`, `duration`.

## 15.3 Dashboards & Alerts
- Dashboard: Sync health per provider, pending outbox, conflict list.
- Alerts:
    - Outbox pending > threshold (e.g., 5000) → P1
    - Adapter error rate spike → P1
    - Reconciliation failures > threshold → P2

## 15.4 Reconciliation Reports
- After each startup reconciliation, create a `recon_report` with counts (created/updated/deleted/conflicts) and attach to `sync_audit`.

---

# 16. Operational Runbook

## 16.1 Normal Flow
- Developers push code; outbox workers process changes.
- Admin triggers `/sync/check` after deployments to ensure providers are consistent.

## 16.2 Provider Outage
- Mark provider `DEGRADED` in config.
- Continue to queue outbox events; reattempt with backoff.
- Alert SRE and admins (optional).

## 16.3 Reconciliation Failures
- Check `recon_task` and retry from last checkpoint.
- If failure persistent due to mapping mismatch, open a manual reconciliation ticket with `sync_audit` as attachment.

## 16.4 Rollback / Undo
- Use Envers to find the last good revision and perform a re-applying with outbox to push to providers.

---

# 17. Testing Strategy & Test Cases

## 17.1 Unit Tests
- Adapter tests: mock provider endpoints, validate transformation & idempotency.
- Hashing tests: consistent attributes_hash generation.
- Conflict resolution tests: simulate DB & provider version differences.

## 17.2 Integration Tests
- Spin up Keycloak/WSO2 test container.
- Seed DB test dataset.
- Run outbox worker, validate provider objects are in the expected state.
- Test startup reconciliation by modifying provider objects directly and ensuring reconciler repairs.

## 17.3 End-to-End Tests
- Simulate full sign-up flows including social login.
- Validate mapping generation and role assignment.

## 17.4 Performance & Scalability Tests
- Load test: bulk create 100k users and measure outbox processing time.
- Reconciliation test: compute time to reconcile 100k users with two providers.

## 17.5 Security Tests
- Pen tests for outbox endpoint, check injection attacks in payload.
- Validate secret handling and key access.

## 17.6 Example Test Cases (detailed)

### Test Case: Startup Reconciliation — Missing Role in Provider
- Precondition: DB has three roles; provider has two (ROLE_USER missing).
- Action: Start the application with reconciling enabled.
- Expected: Reconciler creates missing role in provider, `provider_mapping` entries created, `sync_audit` recorded.

### Test Case: Federated User First Login
- Precondition: No mapping exists for `google:sub123`.
- Action: Simulate provider event indicating federated login with `email_verified=true` and `email=alice@example.com`.
- Expected: If DB has user with same email and `email_verified=true`, mapping created. If not, new `t_user` created and mapping created.

### Test Case: Conflict (Both Changed)
- Precondition: `role` changed in DB (change_number++), provider changed display_name.
- Action: Reconciler has detected both changes since last sync.
- Expected: Per `role` policy (DB_WINS) provider updated. `sync_audit` logged with before/after.

---

# 18. Migration & Rollout Plan

## 18.1 Phase 0 — Prep
- Add tables: `provider_mapping`, `outbox`, `sync_audit`, `recon_task`.
- Add `change_number` column to entities, default 0 (write DB migration).
- Backfill `provider_mapping` for current provider objects (one-off script).
- Establish secrets for provider service accounts.

## 18.2 Phase 1 — Outbox + Adapters (Non-intrusive)
- Implement outbox writer in the codebase but keep outbox processor disabled.
- Implement `IAdapter` skeleton and KeycloakAdapter using a test realm.
- Run integration tests.

## 18.3 Phase 2 — Enable Outbox Processor (Read-only mode)
- Enable processor in `dry-run` mode where it only logs actions (no writes).
- Run for 1–2 weeks and review logs for unexpected changes.

## 18.4 Phase 3 — Live Sync (Gradual)
- Enable writing to one provider in production (Keycloak).
- Monitor metrics and adjust batch sizes.
- Roll out to other providers gradually.

## 18.5 Phase 4 — Full Bi-directional
- Enable provider event ingestion and startup reconciler.
- Ensure conflict policies are set and the admin UI for manual review is available.

---

# 19. Appendix

## 19.1 Sequence Diagrams (ASCII)

### 19.1.1 User created in DB → Provider (Event-driven)
```
App (DB)           OutboxProc          Adapter             Keycloak
--------           -----------         -------             --------
create user
+ change_number++
+ outbox row --------------------> poll event
                                    process
                                    upsertUser(payload) --> create/update user
                                                            <-- 201/200
                                    update provider_mapping
                                    update outbox status DONE
```

### 19.1.2 Federated Login (Google via Keycloak)
```
User           Keycloak          Adapter            App (DB)
----           --------          -------            ------
login -> oauth -> token -> assertion -> webhook ->  find mapping?
                                                   | mapping -> auth
                                                   | no mapping -> find by email
                                                   | email match -> link mapping
                                                   | else -> create user + mapping
                                                   emit outbox USER_CREATED
                                                   adapter -> assign default roles
```
---

# Closing notes

This document is a comprehensive blueprint for building a production-ready IAM synchronization system. It covers high-level architecture, data models, adapter contracts, sync algorithms, conflict resolution, idempotency, monitoring, testing, and operational procedures.