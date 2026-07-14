# Mission

## What this project is

**iGRP Access Management** is the identity, authorization, and audit backbone of the iGRP 3.0 platform. It is a Spring Boot service that acts as:

- **OAuth2 / OpenID Connect Authorization Server** for platform-integrated applications (issues access tokens, ID tokens, refresh tokens; supports authorization-code, client-credentials, and refresh-token grants).
- **OAuth2 Resource Server** protecting its own REST surface.
- **Identity store** for platform users (`t_user`) and machine identities (`t_service_account`).
- **Authorization decision point** — evaluates permission checks (`igrp.<resource>.<action>`) against the caller's roles, applications, and departments.
- **Audit trail** — records every security-relevant action (authentication, authorization decisions, session state changes, administrative configuration changes) in a tamper-evident append-only log.

## Who it serves

| Audience | What they get |
|---|---|
| **iGRP platform applications** (client apps registered under `t_oauth_client`) | Sign users in via OIDC; call the platform's APIs on behalf of users with delegated tokens; call APIs machine-to-machine via service accounts. |
| **iGRP platform end-users** | A single sign-on entry point across every iGRP application; consistent role and permission enforcement. |
| **Platform administrators** | UI-backed CRUD over applications, departments (organizational units), roles, permissions, menus, service accounts, and user role assignments. |
| **Auditors / compliance officers** | Read-only access to the tamper-evident audit trail and the 3 audit report views (Audit Report, Access Report, Settings Report). |
| **Downstream SDK consumers** | A Java SDK (`cv.igrp.platform.access:client`) and a TypeScript SDK for programmatic access from other backends and frontends. |

## Non-negotiable guarantees

1. **Tamper-evident audit trail.** Every write to the audit log is chained to the previous entry via a cryptographic hash; deleting or modifying a row breaks the chain and is detected on validation. Enforced at the database level with an append-only trigger.
2. **Fail-safe audit.** An audit write failure never breaks the user-facing action that triggered it. Audit is best-effort in latency, guaranteed in eventual consistency.
3. **Defense-in-depth authorization.** Every controller endpoint is gated by an explicit permission check. Missing permissions default to deny.
4. **Zero-trust session revocation.** Revoking a session invalidates all tokens issued under that session within one refresh cycle.
5. **Reproducible schema.** All database changes ship as versioned Flyway migrations. No production DDL by hand.
6. **SDK contract stability.** Public REST endpoints and their DTOs are part of the SDK surface; changes require a client SDK version bump.

## Anti-goals

- **Not a general-purpose identity provider.** Keycloak/Auth0 exist for that; this project focuses on the iGRP platform's specific needs (custom permission grid, department-scoped roles, application-scoped menus).
- **Not a fine-grained authorization engine (Zanzibar / Permify).** Permission checks are role-based and permission-based, not tuple-based. A `permify-spring-boot` integration module exists for teams that need it.
- **Not a user-facing UI.** The frontend (`access-management/frontend/application-center`) is a separate submodule.
