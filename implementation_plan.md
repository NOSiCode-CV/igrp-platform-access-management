# WSO2 OIDC Integration Implementation Plan

The objective is to implement the guidelines specified in [docs/OIDC_WSO2.md](file:///c:/Users/gilberto.stavares/iGRP_3_0/igrp-platform-access-management/docs/OIDC_WSO2.md) while keeping the [README.md](file:///c:/Users/gilberto.stavares/iGRP_3_0/igrp-platform-access-management/README.md) system architecture intact. 

## User Review Required
> [!WARNING]
> The DTO files (like [InviteUserDTO.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/application/dto/InviteUserDTO.java)) and controllers (like [UserController.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/users/interfaces/rest/UserController.java)) are marked as "GENERATED AUTOMATICALLY BY iGRP STUDIO". Refactoring the invitation flow to be based exclusively on `username` requires modifying these DTOs to make `email` optional and `username` standard. Please confirm if changing these generated files directly is acceptable or if the generation template itself needs to be updated outside this scope.

> [!WARNING]
> Changing the database schema (removing `unique=true` from `email` in [IGRPUserEntity](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/IGRPUserEntity.java#15-112)) might require a database migration if the tables are already created. I will use `spring.jpa.hibernate.ddl-auto=update` in development, but please confirm this approach.

## Proposed Changes

### Core Security & WSO2 Configuration
To properly integrate WSO2 tokens and roles, and to maintain parameterized endpoints without using clear text:
#### [MODIFY] [application.properties](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/resources/application.properties)
- Add generic IAM parameterization properties (e.g. `igrp.iam.issuer`, `igrp.iam.jwks-url`). Allow parameterization of Authorization, Token, UserInfo endpoints to fulfill Skill 3.
#### [MODIFY] [OAuth2SecurityConfiguration.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/security/OAuth2SecurityConfiguration.java)
- Ensure the `jwtAuthenticationConverter` correctly parses WSO2 roles.

### JWT Token Claims Mapping
#### [MODIFY] [IgrpJwtAuthenticationConverter.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/security/IgrpJwtAuthenticationConverter.java)
- Configure the default `JwtGrantedAuthoritiesConverter` to read the `roles` or `groups` claim standard for WSO2 instead of relying solely on the default `scope` claim mappings. This fulfills Skill 2.

### Decoupling Email from User Identification & Invitations
Currently, `email` is heavily relied upon in the system, particularly during user invitations ([InviteUserCommandHandler.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/users/application/commands/InviteUserCommandHandler.java)). This violates Skill 6.
#### [MODIFY] [IGRPUserEntity.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/IGRPUserEntity.java)
- Remove `unique = true` from the `email` column definition (since emails can be null or absent).
- Ensure `username` acts as the unique identifier.
#### [MODIFY] [InviteUserDTO.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/application/dto/InviteUserDTO.java)
- Add a `username` field. Re-evaluate `email` requirements (make it optional).
#### [MODIFY] [InvitationEntity.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/entity/InvitationEntity.java)
- Replace `email` with `username` as the primary key/reference for invitations.
#### [MODIFY] [InvitationEntityRepository.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/shared/infrastructure/persistence/repository/InvitationEntityRepository.java)
- Change find methods `findByEmailAndStatus` to `findByUsernameAndStatus`.
#### [MODIFY] [InviteUserCommandHandler.java](file:///c:/Users/gilberto.stavares/iGRP_3_0/OIDC/igrp-platform-access-management/src/main/java/cv/igrp/platform/access_management/users/application/commands/InviteUserCommandHandler.java)
- Refactor the invitation flow to accept `username` instead of `email`. If `email` is provided, a notification will be sent, but it is not mandatory for resolving the IAM user or storing the invitation.

## Verification Plan
### Automated Tests
- The build will be verified by running `mvn clean install -DskipTests=false` to ensure all existing and modified services compile and pass standard unit and integration tests.
### Manual Verification
- A manual walkthrough could be performed by making a `POST /api/users/invite` API call payload with a missing email but existing `username` and see if the user gets invited correctly without errors.
