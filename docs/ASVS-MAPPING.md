---
name: ASVS Mapping Document
description: Explicit mapping between OWASP ASVS v5.0.0 requirements and concrete implementation in code/architecture for the iGRP platform access management system.
---

# ASVS-MAPPING.md

**Standard:** OWASP Application Security Verification Standard (ASVS) v5.0.0  
**Target Level:** Level 2  
**System:** igrp_platform_access_management  

This document explicitly maps **ASVS requirements** to their **concrete implementation in code/architecture**, serving as technical evidence for auditing, code review, and governance.

---

## V1 — Architecture, Design and Threat Modeling

### V1.1 — Security Architecture

- **ASVS:** Documented and consistent security architecture
- **Implementation:**
  - `ADR-001.md` defines decisions: STRICT, Invite-only, NIC as identity anchor
  - No-Adapter Architecture (IAM is used solely for OIDC authentication)

**Evidence:** `ADR-001.md`, `NO_ADAPTER_ARCHITECTURE.md`

---

## V2 — Authentication

### V2.1 — Strong and centralized authentication

- **ASVS:** Authentication must be delegated to a secure mechanism
- **Implementation:**
  - AUTENTIKA (OIDC / OAuth2 Authorization Code Flow)
  - JWT validated by Spring Security Resource Server

**Code:**
- `OAuth2SecurityConfiguration.java`
- `JwtDecoderConfiguration.java`

---

### V2.2 — Single and unambiguous identity

- **ASVS:** Each user must have a unique identifier
- **Implementation:**
  - JWT `sub` is always the **NIC**
  - `users.nic` used as the UNIQUE canonical key

**Code:**
- `User` entity (`nic` UNIQUE)
- User resolved by `sub`

---

### V2.4 — Strong authentication mechanisms

- **ASVS:** Support for strong/multi-factor authentication
- **Implementation:**
  - CMD (mobile) / CNI (ID card) provided by AUTENTIKA
  - Use of the `auth_method` claim

**Evidence:** Processing of `auth_method`, `phone_number`, `sub` claims

---

## V3 — Session Management

### V3.1 — Secure session management

- **ASVS:** Sessions must be protected against hijacking and fixation
- **Implementation:**
  - Stateless JWT-based session
  - Validation of `iss`, `aud`, `exp` claims

**Code:**
- `OAuth2 Resource Server` (Spring Security)

---

## V4 — Access Control

### V4.1 — Policy-based access control

- **ASVS:** Explicit rule-based authorization
- **Implementation:**
  - Invite-only pattern
  - User only exists in the domain after accepting an invitation

**Code:**
- Gatekeeper: `existsByNic(sub)` verification

---

### V4.2 — Principle of least privilege

- **ASVS:** Only minimum necessary permissions granted
- **Implementation:**
  - Roles assigned **only** after invitation acceptance
  - Roles stored and evaluated in the DB

**Code:**
- `RespondUserInvitationCommandHandler`

---

## V7 — Error Handling and Logging

### V7.1 — Controlled error messages

- **ASVS:** Errors must not reveal sensitive information
- **Implementation:**
  - Standardized functional errors:
    - `INVITE_REQUIRED`
    - `INVITE_ACCEPT_FAILED_STRICT`

---

### V7.2 — Security event logging

- **ASVS:** Relevant events must be auditable
- **Implementation:**
  - Domain events:
    - `INVITE_REQUIRED_BLOCK`
    - `INVITE_ACCEPTED`
    - `IDENTIFIER_COLLISION`

---

## V9 — Communication

### V9.1 — Data protection in transit

- **ASVS:** Communication must use secure channels
- **Implementation:**
  - HTTPS mandatory
  - OIDC endpoints protected by TLS

---

## V13 — API and Web Services

### V13.1 — API authentication and authorization

- **ASVS:** APIs must require strong authentication
- **Implementation:**
  - JWT mandatory on all business endpoints
  - Limited public endpoints (health, invitation token generation)

---

### V13.2 — Input validation

- **ASVS:** APIs must validate external inputs
- **Implementation:**
  - Validation of `identifierType` and `identifierValue`
  - Normalization (email, phone)

---

## V14 — Configuration

### V14.1 — Secure configuration

- **ASVS:** Secrets and sensitive configurations protected
- **Implementation:**
  - `client_secret` stored outside of code
  - `igrp.keycloak.*` properties removed (No-Adapter)

---

## Compliance Summary

✅ ASVS Level 2 met by design and implementation  
✅ Strong identity anchored to NIC  
✅ STRICT and auditable invitations  
✅ Architecture decoupled from IAM admin APIs
