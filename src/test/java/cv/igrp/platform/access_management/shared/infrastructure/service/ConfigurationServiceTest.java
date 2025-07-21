package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock private IGRPUserEntityRepository userRepository;
    @Mock private ApplicationEntityRepository applicationRepository;
    @Mock private DepartmentEntityRepository departmentRepository;
    @Mock private MenuEntryEntityRepository menuEntryRepository;
    @Mock private PermissionEntityRepository permissionRepository;
    @Mock private RoleEntityRepository roleRepository;
    @Mock private CustomFieldEntityRepository propertyRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ConfigurationService configurationService;

    private IGRPUserEntity user;

    @BeforeEach
    void setup() {
        user = new IGRPUserEntity();
        user.setId(1);
        user.setUsername("superadmin");
        user.setEmail("superadmin@igrp.cv");
        user.setName("Super Admin");
        user.setRoles(new ArrayList<>());
    }

    @Test
    @DisplayName("Should create superadmin user when not exists")
    void createSuperAdminUser_createsUserIfNotExists() {
        when(userRepository.save(any(IGRPUserEntity.class))).thenReturn(user);

        configurationService.createSuperAdminUser();

        verify(userRepository).save(any(IGRPUserEntity.class));
    }

    @Test
    @DisplayName("Should assign role to superadmin if not already assigned")
    void assignRoleToSuperAdminUser_assignsRole() {
        RoleEntity role = new RoleEntity();
        role.setName("superadmin");

        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        configurationService.assignRoleToSuperAdminUser(role);

        assertTrue(user.getRoles().contains(role));
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should not reassign role if already assigned")
    void assignRoleToSuperAdminUser_doesNotDuplicateRole() {
        RoleEntity role = new RoleEntity();
        role.setName("superadmin");

        user.getRoles().add(role);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));

        configurationService.assignRoleToSuperAdminUser(role);

        verify(userRepository, never()).save(user);
    }

    @Test
    @DisplayName("Should create default department")
    void createDefaultDepartment_savesDepartment() {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setCode("DEPT_IGRP");

        when(departmentRepository.save(any())).thenReturn(dept);

        DepartmentEntity result = configurationService.createDefaultDepartment();

        assertEquals("DEPT_IGRP", result.getCode());
        verify(departmentRepository).save(any(DepartmentEntity.class));
    }

    @Test
    @DisplayName("Should create default application")
    void createDefaultApp_savesApp() {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setId(1);
        ApplicationEntity app = new ApplicationEntity();
        app.setType(AppType.SYSTEM);

        when(applicationRepository.save(any())).thenReturn(app);

        ApplicationEntity result = configurationService.createDefaultApp(dept);

        assertEquals(AppType.SYSTEM, result.getType());
        verify(applicationRepository).save(any(ApplicationEntity.class));
    }

    @Test
    @DisplayName("Should create default permission")
    void createDefaultPermission_savesPermission() {
        ApplicationEntity app = new ApplicationEntity();
        PermissionEntity perm = new PermissionEntity();
        perm.setName("manage_access");

        when(permissionRepository.save(any())).thenReturn(perm);

        PermissionEntity result = configurationService.createDefaultPermission(app);

        assertEquals("manage_access", result.getName());
        verify(permissionRepository).save(any(PermissionEntity.class));
    }

    @Test
    @DisplayName("Should create default role")
    void createDefaultRole_savesRole() {
        DepartmentEntity dept = new DepartmentEntity();
        PermissionEntity perm = new PermissionEntity();
        perm.setId(1);

        RoleEntity role = new RoleEntity();
        role.setName("superadmin");

        when(roleRepository.save(any())).thenReturn(role);

        RoleEntity result = configurationService.createDefaultRole(dept, perm);

        assertEquals("superadmin", result.getName());
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    @DisplayName("Should handle full system initialization without error")
    void initializeSystemConfiguration_shouldRunSuccessfully() {
        when(userRepository.existsByUsername("superadmin")).thenReturn(false);
        when(departmentRepository.existsByCode("DEPT_IGRP")).thenReturn(false);
        when(applicationRepository.existsByType(AppType.SYSTEM)).thenReturn(false);
        when(permissionRepository.existsByName("manage_access")).thenReturn(false);
        when(roleRepository.existsByName("superadmin")).thenReturn(false);

        DepartmentEntity dept = new DepartmentEntity();
        dept.setCode("DEPT_IGRP");

        ApplicationEntity app = new ApplicationEntity();
        app.setType(AppType.SYSTEM);
        app.setId(100);

        PermissionEntity perm = new PermissionEntity();
        perm.setName("manage_access");

        RoleEntity role = new RoleEntity();
        role.setName("superadmin");

        when(departmentRepository.save(any())).thenReturn(dept);
        when(applicationRepository.save(any())).thenReturn(app);
        when(permissionRepository.save(any())).thenReturn(perm);
        when(roleRepository.save(any())).thenReturn(role);
        when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        configurationService.initializeSystemConfiguration();

        verify(departmentRepository).save(any());
        verify(applicationRepository).save(any());
        verify(permissionRepository).save(any());
        verify(roleRepository).save(any());
        verify(userRepository, times(2)).save(any());
    }
}
