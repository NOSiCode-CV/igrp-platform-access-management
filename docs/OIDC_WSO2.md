---
description: Instructions and Skills for OIDC integration with WSO2
---
# OIDC Integration with WSO2

This document serves to introduce *skills* and guidelines that the agent must follow when analyzing or modifying the OpenID Connect (OIDC) integration focused on the WSO2 Identity Server in this project. In particular, this file can be read by the agent to automatically adapt its behavior and context.

## Application Context

- **Identity Provider (IdP)**: WSO2 Identity Server
- **Protocol**: OpenID Connect (OIDC) / OAuth 2.0
- **Project**: igrp-platform-access-management (iGRP 3.0)
- **Goal**: Unified authentication, Single Sign-On (SSO) and authorization based on Roles/Claims.

## Action Guidelines for the Agent

Whenever you act on code related to OIDC and WSO2, you must observe the following rules:

### 1. Security Configurations and Properties
- **Never insert credentials (client_id, client_secret) in clear text in the code.** Always use environment variable references (`${VARIABLE}`) in files like `application.yml`, `.env` or equivalent configurations that Spring Boot/Quarkus supports.
- WSO2 URLs (such as Authorization, Token and UserInfo endpoints) must be parameterized, to transparently support Dev, Test, and Prod environments.

### 2. Validation and Mapping of JWT Tokens
- Token validation must be delegated to the security module using the *JWKS* URL provided by WSO2 (`/oauth2/jwks`).
- The system needs to extract the essential *claims* from the JWT (such as `sub` and user `roles`/`groups`). Ensure that the authority converter (e.g., in Spring Security, `JwtAuthenticationConverter`) correctly maps the WSO2 permissions to the internal iGRP profiles.

### 3. Essential WSO2 Reference Endpoints
It is critical to know the standard URL structure of WSO2 for OIDC:
- **Authorization Endpoint**: `/oauth2/authorize`
- **Token Endpoint**: `/oauth2/token`
- **User Info Endpoint**: `/oauth2/userinfo`
- **JWK Set Endpoint**: `/oauth2/jwks`
- **Logout Endpoint**: `/oidc/logout`

### 4. Authentication Error Resolution (Troubleshooting)
Whenever you are prompted to solve bugs related to Login:
1. Start by reviewing the token emission and integrity on the Client side.
2. Check if the `redirect_uris` configuration in the WSO2 management console matches the application's destination (`callback`).
3. Ensure that the scopes (`scopes` like `openid`, `profile`, `email`) requested in the application configuration are allowed in the associated Service Provider in WSO2.

## Additional References
Any refactoring or new implementation of IAM (Identity and Access Management) must cross-reference the documentation in `IAM_SYNCHRONIZATION.md` and `PERMISSION_MANAGEMENT_SPECS.md` (present in this same folder).
