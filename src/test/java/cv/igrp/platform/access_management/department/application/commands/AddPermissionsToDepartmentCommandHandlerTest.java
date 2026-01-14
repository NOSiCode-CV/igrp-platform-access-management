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
 * Tests AddPermissionsToDepartmentCommandHandler across various scenarios:
 * - success path
 * - department not found
 * - permission not found
 * - duplicate permissions already associated
 */
@ExtendWith(MockitoExtension.class)
public class AddPermissionsToDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private PermissionEntityRepository permissionRepository;

    @InjectMocks
    private AddPermissionsToDepartmentCommandHandler handler;

    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setId(1);
        department.setName("D1");
        department.setCode("DEP1");
    }

    @Test
    void testHandle_Success() {
        AddPermissionsToDepartmentCommand command =
                new AddPermissionsToDepartmentCommand(
                        List.of("perm.read", "perm.write"),
                        "DEP1"
                );

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>());

        PermissionEntity permWrite = new PermissionEntity();
        permWrite.setName("perm.write");
        permWrite.setDepartments(new HashSet<>());

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read"))
                .thenReturn(permRead);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.write"))
                .thenReturn(permWrite);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertTrue(permRead.getDepartments().contains(department));
        assertTrue(permWrite.getDepartments().contains(department));

        verify(permissionRepository).save(permRead);
        verify(permissionRepository).save(permWrite);
    }

    @Test
    void testHandle_DepartmentNotFound() {
        AddPermissionsToDepartmentCommand command =
                new AddPermissionsToDepartmentCommand(
                        List.of("perm.read"),
                        "DEP1"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void testHandle_PermissionNotFound() {
        AddPermissionsToDepartmentCommand command =
                new AddPermissionsToDepartmentCommand(
                        List.of("perm.unknown"),
                        "DEP1"
                );

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.unknown"))
                .thenReturn(null);

        assertThrows(NullPointerException.class, () -> handler.handle(command));
        verify(permissionRepository, never()).save(any());
    }

    @Test
    void testHandle_PermissionAlreadyAssigned() {
        AddPermissionsToDepartmentCommand command =
                new AddPermissionsToDepartmentCommand(
                        List.of("perm.read"),
                        "DEP1"
                );

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>(List.of(department)));

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1"))
                .thenReturn(department);

        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read"))
                .thenReturn(permRead);

        ResponseEntity<String> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertEquals(1, permRead.getDepartments().size());

        verify(permissionRepository).save(permRead);
    }
}