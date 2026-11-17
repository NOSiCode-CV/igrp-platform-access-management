package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountRoleAssignment;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimsEnrichmentServiceTest {

    @Mock private OAuthClientJpaRepository oauthClientRepository;
    @Mock private ServiceAccountJpaRepository serviceAccountRepository;
    @Mock private UserIdentityJpaRepository userIdentityRepository;
    @Mock private IGRPUserEntityRepository userRepository;

    private ClaimsEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new ClaimsEnrichmentService(oauthClientRepository, serviceAccountRepository,
                userIdentityRepository, userRepository);
    }

    @Test
    void mapSubjectReturnsInternalIdWhenIdentityExists() {
        IGRPUserEntity user = new IGRPUserEntity();
        String uid = "00000000-0000-0000-0000-000000000042";
        user.setId(uid);

        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(UUID.randomUUID());
        identity.setProvider("external-idp");
        identity.setUserId("abc-123");
        identity.setUser(user);

        when(userIdentityRepository.findActiveByProviderAndUserId("external-idp", "abc-123"))
                .thenReturn(Optional.of(identity));

        assertEquals(uid, service.mapSubject("external-idp", "abc-123"));
    }

    /**
     * mapSubject must use the status-filtered lookup, never the raw one.
     * The raw variant returns identities of DELETED / INACTIVE users, which
     * would put a soft-deleted user's id in the JWT `sub` — a token that
     * authenticates but is denied by every authorization check. This test
     * previously stubbed the raw method and silently drifted when the
     * production call site moved to the filtered one.
     */
    @Test
    void mapSubjectUsesTheStatusFilteredLookup() {
        when(userIdentityRepository.findActiveByProviderAndUserId("external-idp", "abc-123"))
                .thenReturn(Optional.empty());

        service.mapSubject("external-idp", "abc-123");

        verify(userIdentityRepository).findActiveByProviderAndUserId("external-idp", "abc-123");
        verify(userIdentityRepository, never()).findByProviderAndUserId("external-idp", "abc-123");
    }

    @Test
    void mapSubjectReturnsNullWhenIdentityAbsent() {
        when(userIdentityRepository.findActiveByProviderAndUserId("x", "y")).thenReturn(Optional.empty());
        assertNull(service.mapSubject("x", "y"));
        // Null arguments short-circuit before the repository is consulted.
        assertNull(service.mapSubject(null, "y"));
        assertNull(service.mapSubject("x", null));
    }

    @Test
    void mapEmailReturnsInternalIdWhenUserExists() {
        IGRPUserEntity user = new IGRPUserEntity();
        String uid = "00000000-0000-0000-0000-000000000043";
        user.setId(uid);

        when(userRepository.findActiveOrTemporaryByEmailIgnoreCase("demo@nosi.cv"))
                .thenReturn(java.util.List.of(user));

        assertEquals(uid, service.mapEmail("external-idp", "demo@nosi.cv"));
    }

    @Test
    void mapEmailReturnsNullWhenUserAbsent() {
        when(userRepository.findActiveOrTemporaryByEmailIgnoreCase("missing@nosi.cv"))
                .thenReturn(java.util.List.of());

        assertNull(service.mapEmail("external-idp", "missing@nosi.cv"));
        assertNull(service.mapEmail(null, "missing@nosi.cv"));
        assertNull(service.mapEmail("external-idp", null));
    }

    @Test
    void mapEmailPicksActiveOverTemporaryWhenBothExist() {
        // Repository orders ACTIVE before TEMPORARY; mapEmail picks first.
        // Data-corruption fallback: duplicate-email rows don't crash the flow.
        IGRPUserEntity active = new IGRPUserEntity();
        active.setId("00000000-0000-0000-0000-0000000000a1");
        IGRPUserEntity temporary = new IGRPUserEntity();
        temporary.setId("00000000-0000-0000-0000-0000000000a2");

        when(userRepository.findActiveOrTemporaryByEmailIgnoreCase("dup@nosi.cv"))
                .thenReturn(java.util.List.of(active, temporary));

        assertEquals("00000000-0000-0000-0000-0000000000a1",
                service.mapEmail("external-idp", "dup@nosi.cv"));
    }

    @Test
    @DisplayName("#33: a user's access token says who the user is, never their roles, permissions or metadata")
    void buildClaimsIsIdentityOnly() {
        IGRPUserEntity user = userWithRolePermissionsAndMetadata();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        Map<String, Object> claims = service.buildClaims(user.getId(), "igrp-access-management",
                Set.of("openid", "profile", "email"));

        assertEquals(Map.of("is_super_admin", false, "name", "Demo User", "preferred_username", "demo",
                "email", "demo@nosi.cv"), claims);
    }

    @Test
    @DisplayName("#33: the ID token carries the profile the scopes allow, and no roles or permissions")
    void buildIdTokenClaimsIsTheProfile() {
        IGRPUserEntity user = userWithRolePermissionsAndMetadata();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        Map<String, Object> claims = service.buildIdTokenClaims(user.getId(), Set.of("openid", "profile"));

        assertEquals("pt-CV", claims.get("locale"));
        assertEquals("demo", claims.get("preferred_username"));
        assertEquals(false, claims.get("is_super_admin"));
        assertFalse(claims.containsKey("email"), "no email scope");
        assertTrue(Collections.disjoint(claims.keySet(),
                Set.of("permissions", "resource_access", "selectedRole", "org", "metadata")));
    }

    private static IGRPUserEntity userWithRolePermissionsAndMetadata() {
        PermissionEntity perm = new PermissionEntity();
        perm.setName("IGRP_USERS_VIEW");
        RoleEntity role = new RoleEntity();
        role.setCode("ROLE_ADMIN");
        role.setPermissions(new HashSet<>(Set.of(perm)));
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("IGRP_ORG");
        role.setDepartment(department);

        IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000007");
        user.setUsername("demo");
        user.setName("Demo User");
        user.setEmail("demo@nosi.cv");
        user.setActiveRole(role);
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("locale", "pt-CV");
        md.put("is_super_admin", true);
        user.setMetadata(md);
        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role);
        List<UserRoleAssignment> assignments = new ArrayList<>();
        assignments.add(assignment);
        user.setUserRoleAssignments(assignments);
        return user;
    }

    @Test
    void buildClaimsMarksSuperAdminWhenActiveAssignmentExists() {
        RoleEntity superAdminRole = new RoleEntity();
        superAdminRole.setId(1);
        superAdminRole.setCode(SUPER_ADMIN_ROLE);
        // The role itself, in the iGRP department: a look-alike elsewhere is not root (#20).
        DepartmentEntity igrpDepartment = new DepartmentEntity();
        igrpDepartment.setCode("DEPT_IGRP");
        superAdminRole.setDepartment(igrpDepartment);

        IGRPUserEntity user = new IGRPUserEntity();
        String userId = "00000000-0000-0000-0000-0000000000ad";
        user.setId(userId);
        user.setActiveRole(superAdminRole);

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUser(user);
        assignment.setRole(superAdminRole);
        assignment.setExpiresAt(LocalDateTime.now().plusDays(1));
        user.setUserRoleAssignments(List.of(assignment));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Map<String, Object> claims = service.buildClaims(userId, "client-a", Set.of("openid"));

        assertEquals(true, claims.get("is_super_admin"));
    }

    @Test
    void buildClaimsToleratesMissingUserOrClient() {
        assertEquals(Map.of(), service.buildClaims(null, "no-such", Set.of("openid")));
        assertEquals(Map.of(), service.buildIdTokenClaims(null, Set.of("openid")));
    }

    @Test
    @DisplayName("#33: a service account's token identifies it as one and lists no permissions")
    void buildClaimsIdentifiesServiceAccount() {
        UUID serviceAccountId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        PermissionEntity permission = new PermissionEntity();
        permission.setName("igrp.m2m.sync");
        permission.setStatus(Status.ACTIVE);

        RoleEntity role = new RoleEntity();
        role.setId(7);
        role.setCode("M2M_SYNC");
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>(Set.of(permission)));

        OAuthClientEntity client = new OAuthClientEntity();
        client.setId(UUID.randomUUID());
        client.setClientId("client-a");
        client.setActive(true);
        ApplicationEntity app = new ApplicationEntity();
        app.setCode("M2M_APP");
        client.setApplication(app);

        ServiceAccountEntity serviceAccount = new ServiceAccountEntity();
        serviceAccount.setId(serviceAccountId);
        serviceAccount.setName("Client A");
        serviceAccount.setActive(true);
        serviceAccount.setOauthClient(client);
        serviceAccount.setApplication(app);
        serviceAccount.setRoleAssignments(new HashSet<>());
        serviceAccount.getRoleAssignments().add(new ServiceAccountRoleAssignment(serviceAccount, role, null));

        when(userRepository.findById(serviceAccountId.toString())).thenReturn(Optional.empty());
        when(serviceAccountRepository.findByIdWithRolesAndPermissions(serviceAccountId))
                .thenReturn(Optional.of(serviceAccount));

        Map<String, Object> claims = service.buildClaims(serviceAccountId.toString(), "client-a", Set.of());

        // #33: a service account carries what identifies it, not the user claims (no is_super_admin).
        assertEquals(Map.of(
                ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE, ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT,
                ServiceAccountTokenClaims.CLAIM_SERVICE_ACCOUNT_ID, serviceAccountId.toString(),
                ServiceAccountTokenClaims.CLAIM_CLIENT_ID, "client-a",
                ServiceAccountTokenClaims.CLAIM_APPLICATION_CODE, "M2M_APP"), claims);
    }
}
