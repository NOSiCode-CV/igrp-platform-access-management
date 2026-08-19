package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.DepartmentScopeChangedEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Mock private ScopeService scopeService;

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

    /**
     * Cascade: unlinking a permission from a department must also strip it from every role
     * in that department AND in every non-deleted descendant department, and publish
     * per-role {@link RolePermissionChangedEvent} + per-department
     * {@link DepartmentScopeChangedEvent} so downstream session invalidation runs.
     */
    @Test
    void testHandle_CascadesToRolesInDepartmentAndDescendants() {
        // Given: DEP-01 has a child CHILD-01. Both departments have a role holding perm.read.
        DepartmentEntity childDept = new DepartmentEntity();
        childDept.setId(200);
        childDept.setCode("CHILD-01");
        childDept.setName("Child Department");
        childDept.setChildrenids(new ArrayList<>());

        department.setChildrenids(new ArrayList<>(List.of(childDept)));

        PermissionEntity permRead = new PermissionEntity();
        permRead.setName("perm.read");
        permRead.setDepartments(new HashSet<>(List.of(department, childDept)));

        RoleEntity roleInParent = new RoleEntity();
        roleInParent.setId(10);
        roleInParent.setCode("ADMIN");
        roleInParent.setDepartment(department);
        roleInParent.setPermissions(new HashSet<>(List.of(permRead)));

        RoleEntity roleInChild = new RoleEntity();
        roleInChild.setId(11);
        roleInChild.setCode("READER");
        roleInChild.setDepartment(childDept);
        roleInChild.setPermissions(new HashSet<>(List.of(permRead)));

        RemovePermissionsFromDepartmentCommand command =
                new RemovePermissionsFromDepartmentCommand(List.of("perm.read"), "DEP-01");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEP-01")).thenReturn(department);
        when(permissionRepository.findByNameAndStatusNotDeleted("perm.read")).thenReturn(permRead);
        when(roleRepository.findIdsByDepartmentIdIn(Set.of(100, 200)))
                .thenReturn(Set.of(10, 11));
        when(roleRepository.findAllById(Set.of(10, 11)))
                .thenReturn(List.of(roleInParent, roleInChild));

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertEquals(204, response.getStatusCode().value());

        // 1) Permission unlinked from both departments in the subtree.
        assertFalse(permRead.getDepartments().contains(department));
        assertFalse(permRead.getDepartments().contains(childDept));
        verify(permissionRepository).save(permRead);

        // 2) Permission scrubbed from every role in the subtree.
        assertTrue(roleInParent.getPermissions().isEmpty());
        assertTrue(roleInChild.getPermissions().isEmpty());
        verify(roleRepository).save(roleInParent);
        verify(roleRepository).save(roleInChild);

        // 3) Per-role invalidation events published (one per modified role).
        ArgumentCaptor<RolePermissionChangedEvent> roleEvents =
                ArgumentCaptor.forClass(RolePermissionChangedEvent.class);
        verify(eventPublisher, times(2)).publishRolePermissionChanged(roleEvents.capture());
        List<String> emittedRoleCodes = roleEvents.getAllValues().stream()
                .map(RolePermissionChangedEvent::getRoleCode).sorted().toList();
        assertEquals(List.of("ADMIN", "READER"), emittedRoleCodes);
        roleEvents.getAllValues().forEach(e ->
                assertEquals("PERMISSIONS_REMOVED", e.getChangeType()));

        // 4) Per-department scope-change events published for both touched departments.
        ArgumentCaptor<DepartmentScopeChangedEvent> deptEvents =
                ArgumentCaptor.forClass(DepartmentScopeChangedEvent.class);
        verify(eventPublisher, times(2)).publishDepartmentScopeChanged(deptEvents.capture());
        List<String> emittedDeptCodes = deptEvents.getAllValues().stream()
                .map(DepartmentScopeChangedEvent::getDepartmentCode).sorted().toList();
        assertEquals(List.of("CHILD-01", "DEP-01"), emittedDeptCodes);
        deptEvents.getAllValues().forEach(e ->
                assertEquals(DepartmentScopeChangedEvent.CHANGE_PERMISSIONS, e.getChangeType()));
    }
}
