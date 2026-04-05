---
name: Identity-and-Invitation
description: Guidelines for multi-identifier authentication anchored on NIC. Enforces STRICT invite-only access and maps identifiers (Email, Phone, NIC) exclusively from JWT claims without external IAM adapter dependencies (No-Adapter).
---
# SKILL — Identity & Invitation (STRICT + NIC Anchor + Invite-only)

**Project/Repo:** `igrp_platform_access_management`  
**Goal:** Remove the email requirement and support identification/login via **Email**, **Phone**, or **NIC**, keeping everything linked to the same user (NIC).  
**Policy:** `STRICT` + `Invite-only (Option A)` + `No-Adapter`.

---

## Skill 0 — Baseline (immutable)

- **NIC is the anchor**: `sub` from AUTENTIKA is always the **NIC** and acts as the user's canonical key.  
- **Email/Phone are optional**: they may exist, but are not mandatory.
- **Invite-only**: if the authenticated user does not exist in the DB, block access and require invitation acceptance.
- **STRICT**: accept invitations **only** with the primary identifier of the authentication method used during acceptance.
- **No-Adapter**: no admin calls to the IAM; only JWT verification + claims extraction.

---

## Skill 1 — Data Model (NIC-anchored)

### `users`
- `id` (UUID)
- `nic` (string) **UNIQUE NOT NULL**  ← comes from `sub`
- `display_name` (optional)
- timestamps

### `user_identifiers` (secondary)
- `id`
- `user_id` (FK)
- `type` ENUM(`EMAIL`, `PHONE`)
- `value_normalized`
- `verified` boolean
- UNIQUE(`type`, `value_normalized`)

**Normalization**
- EMAIL: lower+trim
- PHONE: E.164 (when possible) / trim

---

## Skill 2 — Mapping AUTH_METHOD → Primary Identifier

Implement **core** function:

- `CMD` → `PHONE` using the `phone_number` claim
- `CNI` → `NIC` using `sub`
- `CREDENTIALS` (custom credentials) → `EMAIL` using the `email` claim

**Errors (STRICT)**
- `CMD` without `phone_number` → `PRIMARY_IDENTIFIER_MISSING`
- Custom credentials without `email` → `PRIMARY_IDENTIFIER_MISSING`

---

## Skill 3 — Gatekeeper (Invite-only, Option A)

On all business endpoints:

1) Validate JWT (issuer/signature/exp)
2) Extract `nic = sub`
3) Check if `users.nic == nic` exists

If it **does not exist** and the endpoint is **not** invitation acceptance:

```json
{
  "error": "INVITE_REQUIRED",
  "message": "Unregistered user. You must accept an invitation to enable access."
}
```

---

## Skill 4 — Invitations (Identifier-first + STRICT-by-design)

### 4.1. Invitation Creation
Suggested DTO:

```json
{
  "identifierType": "EMAIL|PHONE|NIC",
  "identifierValue": "string",
  "roles": ["..."] ,
  "expiresAt": "ISO-8601"
}
```

**Automatically derive `allowedAuthMethods`**:
- `EMAIL` invitation → `[CREDENTIALS]`
- `PHONE` invitation → `[CMD]`
- `NIC` invitation → `[CNI]`

---

## Skill 5 — Accept Invitation (STRICT + NIC anchor)

### Input
- `invitationId`
- OIDC Security Context (`IgrpOidcUser` mapped from JWT)

### Steps
1) Extract `IgrpOidcUser` (UserProfile) from `SecurityContext`
2) `nic = sub`
3) `method = auth_method`
4) `primary = getPrimaryIdentifier(method, claims)`
5) Load invitation (PENDING)
6) Validate STRICT:
   - `method in inv.allowedAuthMethods`
   - `inv.identifierType == primary.type`
   - `normalize(inv.identifierValue) == normalize(primary.value)`
7) Resolve user:
   - `user = findByNic(nic)`
   - if `null` → `createUser(nic, displayName=claims.name)`
8) Persist secondary identifiers (best-effort):
   - if `email` exists → upsert EMAIL (verified)
   - if `phone_number` exists → upsert PHONE (verified)
9) Assign roles in the DB (DB-only)
10) Mark invitation as `ACCEPTED`

---

## Skill 6 — Collision and Security

- Global UNIQUE for `EMAIL` and `PHONE`:
  - if email/phone already belongs to another `user_id` → trigger `IDENTIFIER_COLLISION` error
- Register audits:
  - `INVITE_REQUIRED_BLOCK`
  - `INVITE_ACCEPTED`
  - `INVITE_ACCEPT_FAILED_STRICT_MISMATCH`
  - `IDENTIFIER_COLLISION`

---

## Skill 7 — No-Adapter Compliance (what NOT to do)

- Do not call `resolveUser` / IAM admin APIs.
- Do not synchronize roles/departments/permissions with the provider.
- Do not create users outside the invitation flow.

---

## Acceptance Criteria (Done)

- Email is not mandatory in any flow.
- Access for users not existing in the DB is blocked (invite-only).
- Invitation acceptance obeys STRICT rules and is deterministic.
- `sub` (NIC) consistently links the same user.
- No dependency on IAM admin operations.
