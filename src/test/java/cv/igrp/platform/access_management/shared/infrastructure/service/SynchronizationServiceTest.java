package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.auth.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled
class SynchronizationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IAdapter adapter;

    @InjectMocks
    private SynchronizationService synchronizationService;

    private DepartmentInfo testDepartment;
    private ApplicationInfo testApplication;
    private RoleInfo testRole;
    private PermissionInfo testPermission;
    private ResourceInfo testResource;
    private UserIdentity testUser;

    @BeforeEach
    void setUp() {
        testDepartment = new DepartmentInfo();
        testDepartment.setCode("DEPT_TEST");
        testDepartment.setName("Test Department");
        testDepartment.setDescription("Test Department Description");
        testDepartment.setParentDepartment(null);
        testDepartment.setStatus("ACTIVE");

        testApplication = new ApplicationInfo();
        testApplication.setCode("APP_TEST");
        testApplication.setName("Test Application");
        testApplication.setDescription("Test Application Description");
        testApplication.setDepartmentCode("DEPT_TEST");
        testApplication.setStatus("ACTIVE");
        testApplication.setType("APPLICATION");

        testRole = new RoleInfo();
        testRole.setName("DEPT_TEST.role1");
        testRole.setDescription("Test Role");
        testRole.setDepartmentCode("DEPT_TEST");
        testRole.setStatus("ACTIVE");

        testPermission = new PermissionInfo();
        testPermission.setName("perm1");
        testPermission.setDescription("Test Permission");
        testPermission.setStatus("ACTIVE");

        testResource = new ResourceInfo();
        testResource.setName("resource1");
        testResource.setDescription("Test Resource");
        testResource.setUris(List.of("/api/test"));
        testResource.setScopes(List.of("read", "write"));
        testResource.setStatus("ACTIVE");

        testUser = new UserIdentity() {
            @Override public String getId() { return "user1"; }
            @Override public String getUsername() { return "testuser"; }
            @Override public String getFirstName() { return "Test"; }
            @Override public String getLastName() { return "User"; }
            @Override public String getEmail() { return "test@example.com"; }
            @Override public boolean isEnabled() { return true; }
            @Override public String getExternalId() { return "ext123"; }
            @Override public boolean isEmailVerified() { return true; }
        };
    }

    @Test
    @DisplayName("startupReconciliation - should sync all entities successfully")
    void startupReconciliation_syncsAllEntitiesSuccessfully() throws IAMException {
        // Departments
        doReturn(List.of(testDepartment)).when(jdbcTemplate)
                .query(contains("FROM t_department"), any(RowMapper.class), eq("ACTIVE"));
        // Applications
        doReturn(List.of(testApplication)).when(jdbcTemplate)
                .query(contains("FROM t_application"), any(RowMapper.class), eq("ACTIVE"));
        // Roles
        doReturn(List.of(testRole)).when(jdbcTemplate)
                .query(contains("FROM t_role"), any(RowMapper.class), eq("ACTIVE"));
        // Permissions
        doReturn(List.of(testPermission)).when(jdbcTemplate)
                .query(contains("FROM t_permission"), any(RowMapper.class), eq("ACTIVE"));
        // Resources (ResultSetExtractor)
        doReturn(List.of(testResource)).when(jdbcTemplate)
                .query(contains("FROM t_resource"), any(ResultSetExtractor.class), eq("ACTIVE"));

        // Role-Permission mappings
        doReturn(Map.of("perm1", Set.of("role1", "role2"))).when(jdbcTemplate)
                .query(contains("FROM t_role_permission"), any(ResultSetExtractor.class), eq("ACTIVE"), eq("ACTIVE"));
        // User-Role mappings
        doReturn(Map.of("testuser", Map.of("DEPT_TEST", Set.of("role1")))).when(jdbcTemplate)
                .query(contains("FROM t_role_users"), any(ResultSetExtractor.class), eq("ACTIVE"), eq("ACTIVE"), eq("ACTIVE"));

        // Adapter returns empty initially
        when(adapter.getAllDepartments()).thenReturn(List.of());
        when(adapter.getAllApplications()).thenReturn(List.of());
        when(adapter.getAllRoles()).thenReturn(List.of());
        when(adapter.getAllPermissions()).thenReturn(List.of());
        when(adapter.getAllResources()).thenReturn(List.of());
        when(adapter.getAllRolePermissions()).thenReturn(Map.of());
        when(adapter.getAllUserRoles()).thenReturn(Map.of());
        when(adapter.getAllUsers()).thenReturn(List.of(testUser));
        when(adapter.resolveUser("testuser")).thenReturn(Optional.of(testUser));

        synchronizationService.startupReconciliation();

        verify(adapter).createDepartment("DEPT_TEST", null);
        verify(adapter).createApplication("DEPT_TEST", "APP_TEST");
        verify(adapter).createRole("DEPT_TEST", "DEPT_TEST.role1");
        verify(adapter).createPermission("perm1", "Test Permission");
        verify(adapter).createResource("resource1", "Test Resource",
                List.of("/api/test"), List.of("read", "write"));
    }

    @Test
    @DisplayName("startupReconciliation - should delete entities from provider that don't exist in DB")
    void startupReconciliation_deletesOrphanedProviderEntities() throws IAMException {
        doReturn(Collections.emptyList()).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class));

        DepartmentInfo orphanDept = new DepartmentInfo();
        orphanDept.setCode("ORPHAN_DEPT");

        when(adapter.getAllDepartments()).thenReturn(List.of(orphanDept));
        when(adapter.getAllApplications()).thenReturn(List.of());
        when(adapter.getAllRoles()).thenReturn(List.of());
        when(adapter.getAllPermissions()).thenReturn(List.of());
        when(adapter.getAllResources()).thenReturn(List.of());
        when(adapter.getAllRolePermissions()).thenReturn(Map.of());
        when(adapter.getAllUserRoles()).thenReturn(Map.of());
        when(adapter.getAllUsers()).thenReturn(List.of());

        synchronizationService.startupReconciliation();

        verify(adapter).deleteDepartment("ORPHAN_DEPT");
    }

    @Test
    @DisplayName("startupReconciliation - should handle IAMException gracefully and continue")
    void startupReconciliation_handlesIAMExceptionGracefully() throws IAMException {
        doReturn(List.of(testDepartment)).when(jdbcTemplate)
                .query(contains("FROM t_department"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testApplication)).when(jdbcTemplate)
                .query(contains("FROM t_application"), any(RowMapper.class), eq("ACTIVE"));

        when(adapter.getAllDepartments()).thenThrow(new IAMException("Provider error"));
        when(adapter.getAllApplications()).thenReturn(List.of());

        assertDoesNotThrow(() -> synchronizationService.startupReconciliation());

        verify(adapter).getAllApplications();
    }

    @Test
    @DisplayName("checkSynchronization - should detect missing entities in provider")
    void checkSynchronization_detectsMissingEntitiesInProvider() throws IAMException {
        doReturn(List.of(testDepartment)).when(jdbcTemplate)
                .query(contains("FROM t_department"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testApplication)).when(jdbcTemplate)
                .query(contains("FROM t_application"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testRole)).when(jdbcTemplate)
                .query(contains("FROM t_role"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testPermission)).when(jdbcTemplate)
                .query(contains("FROM t_permission"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testResource)).when(jdbcTemplate)
                .query(contains("FROM t_resource"), any(ResultSetExtractor.class), eq("ACTIVE"));

        when(adapter.getAllDepartments()).thenReturn(List.of());
        when(adapter.getAllApplications()).thenReturn(List.of());
        when(adapter.getAllRoles()).thenReturn(List.of());
        when(adapter.getAllPermissions()).thenReturn(List.of());
        when(adapter.getAllResources()).thenReturn(List.of());
        when(adapter.getAllRolePermissions()).thenReturn(Map.of());
        when(adapter.getAllUserRoles()).thenReturn(Map.of());

        var syncStatus = synchronizationService.checkSynchronization();

        assertTrue(syncStatus.isNeedsSync());
        assertFalse(syncStatus.getDifferences().get("departments").getMissingInProvider().isEmpty());
    }

    @Test
    @DisplayName("checkSynchronization - should detect missing entities in database")
    void checkSynchronization_detectsMissingEntitiesInDatabase() throws IAMException {
        doReturn(Collections.emptyList()).when(jdbcTemplate)
                .query(anyString(), any(RowMapper.class), eq("ACTIVE"));
        doReturn(Collections.emptyList()).when(jdbcTemplate)
                .query(anyString(), any(ResultSetExtractor.class), any());

        when(adapter.getAllDepartments()).thenReturn(List.of(testDepartment));
        when(adapter.getAllApplications()).thenReturn(List.of(testApplication));
        when(adapter.getAllRoles()).thenReturn(List.of(testRole));
        when(adapter.getAllPermissions()).thenReturn(List.of(testPermission));
        when(adapter.getAllResources()).thenReturn(List.of(testResource));
        when(adapter.getAllRolePermissions()).thenReturn(Map.of());
        when(adapter.getAllUserRoles()).thenReturn(Map.of());

        var syncStatus = synchronizationService.checkSynchronization();

        assertTrue(syncStatus.isNeedsSync());
        assertFalse(syncStatus.getDifferences().get("departments").getMissingInDatabase().isEmpty());
    }

    @Test
    @DisplayName("checkSynchronization - should return no sync needed when entities are in sync")
    void checkSynchronization_returnsNoSyncWhenInSync() throws IAMException {
        doReturn(List.of(testDepartment)).when(jdbcTemplate)
                .query(contains("FROM t_department"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testApplication)).when(jdbcTemplate)
                .query(contains("FROM t_application"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testRole)).when(jdbcTemplate)
                .query(contains("FROM t_role"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testPermission)).when(jdbcTemplate)
                .query(contains("FROM t_permission"), any(RowMapper.class), eq("ACTIVE"));
        doReturn(List.of(testResource)).when(jdbcTemplate)
                .query(contains("FROM t_resource"), any(ResultSetExtractor.class), eq("ACTIVE"));

        when(adapter.getAllDepartments()).thenReturn(List.of(testDepartment));
        when(adapter.getAllApplications()).thenReturn(List.of(testApplication));
        when(adapter.getAllRoles()).thenReturn(List.of(testRole));
        when(adapter.getAllPermissions()).thenReturn(List.of(testPermission));
        when(adapter.getAllResources()).thenReturn(List.of(testResource));
        when(adapter.getAllRolePermissions()).thenReturn(Map.of());
        when(adapter.getAllUserRoles()).thenReturn(Map.of());

        var syncStatus = synchronizationService.checkSynchronization();

        assertFalse(syncStatus.isNeedsSync());
    }
}
