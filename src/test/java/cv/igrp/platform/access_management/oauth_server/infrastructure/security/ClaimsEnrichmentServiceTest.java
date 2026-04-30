package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
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
    @Mock private UserIdentityJpaRepository userIdentityRepository;
    @Mock private IGRPUserEntityRepository userRepository;

    private ClaimsEnrichmentService service;

    @BeforeEach
    void setUp() {
        service = new ClaimsEnrichmentService(oauthClientRepository, userIdentityRepository, userRepository);
    }

    @Test
    void mapSubjectReturnsInternalIdWhenIdentityExists() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(42);

        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(UUID.randomUUID());
        identity.setProvider("external-idp");
        identity.setUserId("abc-123");
        identity.setUser(user);

        when(userIdentityRepository.findByProviderAndUserId("external-idp", "abc-123"))
                .thenReturn(Optional.of(identity));

        assertEquals("42", service.mapSubject("external-idp", "abc-123"));
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

        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(7);
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

        when(userRepository.findById(7)).thenReturn(Optional.of(user));
        when(oauthClientRepository.findByClientId("igrp-access-management")).thenReturn(Optional.of(client));

        Map<String, Object> claims = service.buildClaims("7", "igrp-access-management");

        assertEquals("ROLE_ADMIN", claims.get("selectedRole"));
        assertEquals("IGRP_APP", claims.get("org"));
        @SuppressWarnings("unchecked")
        Set<String> permissions = (Set<String>) claims.get("permissions");
        assertTrue(permissions.contains("IGRP_USERS_VIEW"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resourceAccess = (Map<String, Object>) claims.get("resource_access");
        assertNotNull(resourceAccess.get("igrp-access-management"));
        // metadata is only emitted when non-empty
        @SuppressWarnings("unchecked")
        Map<String, Object> userMd = (Map<String, Object>) claims.get("metadata");
        assertNotNull(userMd, "metadata claim should be emitted when user metadata is non-empty");
        assertEquals("pt-CV", userMd.get("locale"));
    }

    @Test
    void buildClaimsToleratesMissingUserOrClient() {
        when(oauthClientRepository.findByClientId("no-such")).thenReturn(Optional.empty());

        Map<String, Object> claims = service.buildClaims(null, "no-such");

        assertEquals("", claims.get("selectedRole"));
        assertEquals("", claims.get("org"));
        assertNotNull(claims.get("permissions"));
        assertNotNull(claims.get("resource_access"));
    }
}
