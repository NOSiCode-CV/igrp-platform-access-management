---
name: ASVS Mapping Document
description: Explicit mapping between OWASP ASVS v5.0.0 requirements and concrete implementation in code/architecture for the iGRP platform access management system.
---

# ASVS-MAPPING.md

**Standard:** OWASP Application Security Verification Standard (ASVS) v5.0.0  
**Target Level:** Level 2  
**System:** igrp_platform_access_management  

This document provides explicit mapping between **ASVS requirements** and **concrete implementation in code/architecture**, serving as technical evidence for auditing, code review, and governance.

---

## V1 — Architecture, Design and Threat Modeling

### V1.1 — Security Architecture

- **ASVS:** Documented and consistent security architecture
- **Implementation:**
  - `ADR-001.md` defines decisions: STRICT, Invite-only, NIC as anchor
  - No-Adapter Architecture (IAM used only for OIDC authentication)

**Evidence:** `ADR-001.md`, `NO_ADAPTER_ARCHITECTURE.md`

---

## V2 — Authentication

### V2.1 / 2.1.1 — Unique identity + authentication before authorization

- **ASVS:** The application must uniquely identify users and authenticate before access decisions
- **Implementation:**
  - AUTENTIKA (OIDC) as the sole IdP
  - JWT validated (signature, issuer, exp) **before** any access control
  - `sub` from JWT is always the **NIC**
  - `users.nic` is UNIQUE in the DB

**Code:**
- `OAuth2SecurityConfiguration.java`
- `JwtDecoderConfiguration.java`
- User resolver by `sub`

---

### V2.6.1 — Authentication and privilege escalation logging

- **ASVS:** Authentication and privilege escalation events must be logged for auditing/forensics
- **Implementation:**
  - Authentication events:
    - `AUTHENTICATION_SUCCESS`
    - `AUTHENTICATION_FAILURE`
  - Access/privilege events:
    - `INVITE_REQUIRED_BLOCK`
    - `INVITE_ACCEPTED`
    - `INVITE_ACCEPT_FAILED_STRICT_MISMATCH`
    - `IDENTIFIER_COLLISION`
  - Logs include correlation/request id

**Evidence:** Security interceptors/filters, event publisher, structured logging

---

## V3 — Session Management

### V3.1.1 — Session bound to user

- **ASVS:** Session must be bound to the authenticated user
- **Implementation:**
  - Stateless session based on JWT
  - Token contains `sub = NIC`
  - Every request revalidates the token

---

### V3.2.1 — Session state non-modifiable by client

- **ASVS:** Session state/context cannot be altered by the client
- **Implementation:**
  - Signed JWT (JWS)
  - Any modification invalidates the signature

---

### V3.4.1 — Token integrity throughout lifecycle

- **ASVS:** Tokens and session context maintain integrity throughout the entire cycle
- **Implementation:**
  - Validation of `iss`, `aud`, `exp`
  - Controlled clock skew (limited and documented window)
  - Short token lifetime (configured in the IdP)

---

## V4 — Access Control

### V4.1.1 — Server-side access control

- **ASVS:** Access decisions must always be enforced on the server
- **Implementation:**
  - Server-side gatekeeper
  - No decisions based on client/UI state

---

### V4.2.1 — Role/attribute/context-based authorization

- **ASVS:** Access control must use roles, attributes, or context
- **Implementation:**
  - Roles assigned in the DB
  - Context includes `auth_method`, invitation state, NIC

---

### V4.3.1 — Least Privilege / Deny by Default

- **ASVS:** Principle of least privilege with deny-by-default behavior
- **Implementation:**
  - Invite-only (denies access if user does not exist in DB)
  - No JIT provisioning
  - Permissions granted only after explicit invitation acceptance

---

## V7 — Error Handling and Logging

### V7.1 — Controlled errors

- **ASVS:** Error messages must not leak sensitive information
- **Implementation:**
  - Standardized functional errors:
    - `INVITE_REQUIRED`
    - `STRICT_MISMATCH`
  - Technical details printed only in internal logs

---

### V7.2 — Audit and traceability

- **ASVS:** Relevant events must be auditable
- **Implementation:**
  - Structured logs
  - Correlation/request id
  - Security events logged according to V2.6.1

---

## V9 — Communication

### V9.1 — Data protection in transit

- **ASVS:** Communication protected by TLS
- **Implementation:**
  - HTTPS mandatory
  - OIDC and API endpoints only over TLS

---

## V13 — API and Web Services

### V13.1 — API authentication and authorization

- **ASVS:** APIs protected against unauthorized access
- **Implementation:**
  - JWT mandatory on business endpoints
  - Public endpoints explicitly defined

---

### V13.2 — Input validation

- **ASVS:** External inputs must be validated
- **Implementation:**
  - Type/format validation
  - Normalization for email and phone numbers

---

## V14 — Configuration

### V14.1 — Secure configuration / No-Adapter

- **ASVS:** Configurations and secrets protected
- **Implementation:**
  - Secrets stored outside of code
  - Removal of IAM admin/properties (`igrp.keycloak.*`)
  - Security parameters configurable per environment

---

## Compliance Summary

✅ ASVS Level 2 met by design and implementation  
✅ Explicit logging for authentication and privileges  
✅ JWT session with integrity, expiration, and controlled clock skew  
✅ Least privilege and deny-by-default systematically applied
