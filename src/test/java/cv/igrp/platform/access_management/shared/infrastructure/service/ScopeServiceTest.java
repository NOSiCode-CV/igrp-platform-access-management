package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.RequestScopeCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScopeService Tests - Integer User ID Refactor")
class ScopeServiceTest {

    @Mock
    private AuthenticationHelper authenticationHelper;

    @Mock
    private RequestScopeCache cache;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ScopeService scopeService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @Test
    @DisplayName("Should return ActorPrincipal with Integer ID from String subject")
    void testGetActorReturnIntegerId() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("456"); // String from JWT
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ADMIN"),
                new SimpleGrantedAuthority("USER")
        );
        doReturn(authorities).when(authentication).getAuthorities();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(456), anyString()))
                .thenReturn(List.of(1)); // Mock superadmin check

        // Act
        ScopeService.ActorPrincipal actor = scopeService.getActor();

        // Assert
        assertNotNull(actor);
        assertEquals(456, actor.id()); // Verify Integer ID
        assertTrue(actor.roles().contains("ADMIN"));
        assertTrue(actor.roles().contains("USER"));
    }

    @Test
    @DisplayName("Should throw exception when no authentication available")
    void testGetActorNoAuthenticationThrows() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> scopeService.getActor()
        );
        assertEquals("No authentication available in security context", exception.getMessage());
    }

    @Test
    @DisplayName("Should parse Integer subject from different users")
    void testGetActorWithDifferentIntegerIds() {
        // Arrange - Test with different Integer IDs
        String[] subjectIds = {"123", "789", "999"};

        for (String subject : subjectIds) {
            when(authenticationHelper.getSub()).thenReturn(subject);
            when(authentication.getAuthorities()).thenReturn(List.of());
            when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(Integer.parseInt(subject)), anyString()))
                    .thenReturn(List.of());

            // Act
            ScopeService.ActorPrincipal actor = scopeService.getActor();

            // Assert
            assertEquals(Integer.parseInt(subject), actor.id());
        }
    }

    @Test
    @DisplayName("Should identify superadmin user with Integer ID query")
    void testIsSuperAdminUsesIntegerId() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("100");
        // Mock JDBC query to return a result for superadmin check
        when(jdbcTemplate.query(contains("t_role_users"), any(RowMapper.class), eq(100), anyString()))
                .thenReturn(List.of(1)); // Non-empty = superadmin

        // Act
        boolean isSuperAdmin = scopeService.isSuperAdmin();

        // Assert
        assertTrue(isSuperAdmin);
        // Verify that query was called with Integer 100
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(100), eq(cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE));
    }

    @Test
    @DisplayName("Should identify non-superadmin user")
    void testIsNotSuperAdmin() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("200");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(200), anyString()))
                .thenReturn(List.of()); // Empty = not superadmin

        // Act
        boolean isSuperAdmin = scopeService.isSuperAdmin();

        // Assert
        assertFalse(isSuperAdmin);
    }

    @Test
    @DisplayName("Should return false when no authentication for superadmin check")
    void testIsSuperAdminNoAuthReturnsfalse() {
        // Arrange
        when(securityContext.getAuthentication()).thenReturn(null);

        // Act
        boolean isSuperAdmin = scopeService.isSuperAdmin();

        // Assert
        assertFalse(isSuperAdmin);
    }

    @Test
    @DisplayName("Should return all departments for superadmin")
    void testGetVisibleDepartmentIdsForSuperAdmin() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("300");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(300), anyString()))
                .thenReturn(List.of(1)); // superadmin
        when(cache.getVisibleDepartments()).thenReturn(null);

        // Mock department repository
        var dept1 = mock(DepartmentEntity.class);
        var dept2 = mock(DepartmentEntity.class);
        when(departmentRepository.findAll()).thenReturn(List.of(dept1, dept2));

        // Act
        Set<Integer> visibleDepts = scopeService.getVisibleDepartmentIds();

        // Assert
        assertNotNull(visibleDepts);
        verify(cache).setVisibleDepartments(any());
    }

    @Test
    @DisplayName("Should use cached departments when available")
    void testGetVisibleDepartmentIdsUsesCache() {
        // Arrange
        Set<Integer> cachedDepts = Set.of(1, 2, 3);
        when(cache.getVisibleDepartments()).thenReturn(cachedDepts);

        // Act
        Set<Integer> visibleDepts = scopeService.getVisibleDepartmentIds();

        // Assert
        assertEquals(cachedDepts, visibleDepts);
        verify(departmentRepository, never()).findAll();
    }

    @Test
    @DisplayName("Should return all roles for superadmin")
    void testGetVisibleRoleIdsForSuperAdmin() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("400");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(400), anyString()))
                .thenReturn(List.of(1)); // superadmin
        when(cache.getVisibleRoles()).thenReturn(null);

        var role1 = mock(RoleEntity.class);
        var role2 = mock(RoleEntity.class);
        when(roleRepository.findAll()).thenReturn(List.of(role1, role2));

        // Act
        Set<Integer> visibleRoles = scopeService.getVisibleRoleIds();

        // Assert
        assertNotNull(visibleRoles);
        verify(cache).setVisibleRoles(any());
    }

    @Test
    @DisplayName("Should get visible applications for departments")
    void testGetVisibleApplicationIds() {
        // Arrange
        Set<Integer> visibleDepts = Set.of(5, 6);
        Set<Integer> visibleApps = Set.of(50, 51, 52);

        when(cache.getVisibleApplications()).thenReturn(null);
        when(cache.getVisibleDepartments()).thenReturn(visibleDepts);
        when(applicationRepository.findByDepartmentIds(visibleDepts)).thenReturn(visibleApps);

        // Act
        Set<Integer> apps = scopeService.getVisibleApplicationIds();

        // Assert
        assertEquals(visibleApps, apps);
        verify(applicationRepository).findByDepartmentIds(visibleDepts);
        verify(cache).setVisibleApplications(visibleApps);
    }

    @Test
    @DisplayName("ActorPrincipal record should have Integer id field")
    void testActorPrincipalStructure() {
        // Arrange
        Set<String> roles = Set.of("ROLE1", "ROLE2");
        Object principal = new Object();

        // Act
        ScopeService.ActorPrincipal actor = new ScopeService.ActorPrincipal(
                555, // Integer ID
                roles,
                true,
                principal
        );

        // Assert
        assertEquals(555, actor.id());
        assertEquals(roles, actor.roles());
        assertTrue(actor.superAdmin());
        assertEquals(principal, actor.rawPrincipal());
    }

    @Test
    @DisplayName("Should handle Integer.parseInt failure gracefully")
    void testGetActorParseFailure() {
        // Arrange
        when(authenticationHelper.getSub()).thenReturn("invalid-number");

        // Act & Assert
        NumberFormatException exception = assertThrows(
                NumberFormatException.class,
                () -> scopeService.getActor()
        );
        assertNotNull(exception.getMessage());
    }
}
