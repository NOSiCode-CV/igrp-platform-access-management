package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests RemovePermissionsFromDepartmentCommandHandler across multiple scenarios:
 * - success
 * - department not found
 * - permission not found
 * - department not associated with permission
 * - permission associated and properly removed
 */
@ExtendWith(MockitoExtension.class)
public class RemovePermissionsFromDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private PermissionEntityRepository permissionRepository;

    @InjectMocks
    private RemovePermissionsFromDepartmentCommandHandler handler;

    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setId(100);
        department.setCode("DEP-01");
        department.setName("Department A");
    }

    @Test
    void testHandle_Success() {
        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(
                        List.of("perm.read", "perm.write"),
                        "DEP-01"
                );

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>(List.of(department)));

        PermissionEntity permWrite = new PermissionEntity();
        permWrite.setName("perm.write");
        permWrite.setDepartments(new HashSet<>(List.of(department)));

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read"))
                .thenReturn(permRead);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.write"))
                .thenReturn(permWrite);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());

        assertFalse(permRead.getDepartments().contains(department));
        assertFalse(permWrite.getDepartments().contains(department));

        verify(permissionRepository).save(permRead);
        verify(permissionRepository).save(permWrite);
    }

    @Test
    void testHandle_DepartmentNotFound() {
        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(
                        List.of("perm.read"),
                        "DEP-01"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void testHandle_PermissionNotFound() {
        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(
                        List.of("missing.perm"),
                        "DEP-01"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("missing.perm"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void testHandle_DepartmentNotAssociatedWithPermission() {
        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(
                        List.of("perm.read"),
                        "DEP-01"
                );

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>()); // does NOT contain department

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read"))
                .thenReturn(permRead);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());

        assertFalse(permRead.getDepartments().contains(department));
        verify(permissionRepository).save(permRead);
    }

    @Test
    void testHandle_MultiplePermissions_MixedAssociations() {
        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(
                        List.of("perm.read", "perm.extra"),
                        "DEP-01"
                );

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>(List.of(department)));

        PermissionEntity permExtra = new PermissionEntity();
        permExtra.setName("perm.extra");
        permExtra.setDepartments(new HashSet<>()); // already not associated

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read"))
                .thenReturn(permRead);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.extra"))
                .thenReturn(permExtra);

        ResponseEntity<String> response = handler.handle(command);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(permRead.getDepartments().contains(department));
        assertFalse(permExtra.getDepartments().contains(department));
        verify(permissionRepository).save(permRead);
        verify(permissionRepository).save(permExtra);
    }
}