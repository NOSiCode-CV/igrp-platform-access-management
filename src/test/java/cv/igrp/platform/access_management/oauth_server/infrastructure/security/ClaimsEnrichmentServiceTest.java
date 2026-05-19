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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

        when(userIdentityRepository.findByProviderAndUserId("external-idp", "abc-123"))
                .thenReturn(Optional.of(identity));

        assertEquals(uid, service.mapSubject("external-idp", "abc-123"));
    }

    @Test
    void mapSubjectReturnsNullWhenIdentityAbsent() {
        when(userIdentityRepository.findByProviderAndUserId("x", "y")).thenReturn(Optional.empty());
        assertNull(service.mapSubject("x", "y"));
        assertNull(service.mapSubject(null, "y"));
        assertNull(service.mapSubject("x", null));
    }

    @Test
    void buildClaimsEnrichesRolePermissionsAndMetadata() {
        PermissionEntity perm = new PermissionEntity();
        perm.setName("IGRP_USERS_VIEW");

        RoleEntity role = new RoleEntity();
        role.setCode("ROLE_ADMIN");
        role.setPermissions(new HashSet<>(Set.of(perm)));

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("IGRP_ORG");
        role.setDepartment(department);

        IGRPUserEntity user = new IGRPUserEntity();
        String uid7 = "00000000-0000-0000-0000-000000000007";
        user.setId(uid7);
        user.setUsername("demo");
        user.setActiveRole(role);
        Map<String, Object> md = new LinkedHashMap<>();
        md.put("locale", "pt-CV");
        user.setMetadata(md);

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setRole(role);
        List<UserRoleAssignment> assignments = new ArrayList<>();
        assignments.add(assignment);
        user.setUserRoleAssignments(assignments);

        ApplicationEntity app = new ApplicationEntity();
        app.setCode("IGRP_APP");

        OAuthClientEntity client = new OAuthClientEntity();
        client.setClientId("igrp-access-management");
        client.setScopes(new HashSet<>(Set.of("openid", "profile")));
        client.setApplication(app);

        when(userRepository.findById(uid7)).thenReturn(Optional.of(user));
        when(oauthClientRepository.findByClientId("igrp-access-management")).thenReturn(Optional.of(client));

        Map<String, Object> claims = service.buildClaims(
                uid7,
                "igrp-access-management",
                Set.of("openid", "profile")
        );

        assertEquals("ROLE_ADMIN", claims.get("selectedRole"));
        assertEquals("IGRP_ORG", claims.get("org"));
        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) claims.get("permissions");
        assertTrue(permissions.contains("IGRP_USERS_VIEW"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        assertNotNull(resourceAccess.get("igrp-access-management"));
        assertEquals("pt-CV", claims.get("locale"));
        assertFalse(claims.containsKey("metadata"));
    }

    @Test
    void buildClaimsToleratesMissingUserOrClient() {
        when(oauthClientRepository.findByClientId("no-such")).thenReturn(Optional.empty());

        Map<String, Object> claims = service.buildClaims(null, "no-such", Set.of("openid"));

        assertEquals("", claims.get("selectedRole"));
        assertEquals("", claims.get("org"));
        assertNotNull(claims.get("permissions"));
        assertNotNull(claims.get("resource_access"));
    }

    @Test
    void buildClaimsEnrichesServiceAccountRolesAndPermissions() {
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
        when(oauthClientRepository.findByClientId("client-a")).thenReturn(Optional.of(client));
        when(serviceAccountRepository.findByIdWithRolesAndPermissions(serviceAccountId))
                .thenReturn(Optional.of(serviceAccount));

        Map<String, Object> claims = service.buildClaims(serviceAccountId.toString(), "client-a", Set.of());

        assertEquals(ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT,
                claims.get(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE));
        assertEquals(serviceAccountId.toString(), claims.get(ServiceAccountTokenClaims.CLAIM_SERVICE_ACCOUNT_ID));
        assertEquals("", claims.get("org"));
        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) claims.get("permissions");
        assertTrue(permissions.contains("igrp.m2m.sync"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        assertTrue(resourceAccess.containsKey("client-a"));
    }
}
