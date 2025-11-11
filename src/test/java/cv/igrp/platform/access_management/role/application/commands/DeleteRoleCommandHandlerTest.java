package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteRoleCommandHandlerTest {

    @InjectMocks
    private DeleteRoleCommandHandler underTest;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private IAdapter adapter;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleId_NotFound() {
        //... Given
        String roleCode = "admin";
        DeleteRoleCommand command = new DeleteRoleCommand(roleCode);

        //... When
        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
                .thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsFound() {
        // Given
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_IGRP");
        String roleCode = "admin";
        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        RoleEntity parenteRole = new RoleEntity();
        role.setParent(parenteRole);
        role.setDepartment(department);

        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        DeleteRoleCommand command = new DeleteRoleCommand(roleCode);

        // When
        ResponseEntity<Boolean> result = underTest.handle(command);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        assertNotNull(result);
        assertEquals(true, result.getBody());
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldDeleteChildren_WhenRoleIsRoot() {
        // Given
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_IGRP");
        int roleId = 1;
        String roleCode = "admin";
        RoleEntity parenteRole = new RoleEntity();
        parenteRole.setId(roleId);
        parenteRole.setCode(roleCode);
        parenteRole.setStatus(Status.ACTIVE);
        parenteRole.setParent(null);
        parenteRole.setDepartment(department);

        RoleEntity child1 = new RoleEntity();
        child1.setParent(parenteRole);
        child1.setStatus(Status.ACTIVE);
        child1.setDepartment(department);
        RoleEntity child2 = new RoleEntity();
        child2.setStatus(Status.DELETED);
        child2.setParent(parenteRole);
        child2.setDepartment(department);
        RoleEntity child3 = new RoleEntity();
        child3.setStatus(Status.INACTIVE);
        child3.setParent(parenteRole);
        child3.setDepartment(department);

        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED)).thenReturn(Optional.of(parenteRole));
        when(roleRepository.save(parenteRole)).thenReturn(parenteRole);
        when(roleRepository.save(child1)).thenReturn(child1);
        when(roleRepository.save(child2)).thenReturn(child2);
        when(roleRepository.save(child3)).thenReturn(child3);

        DeleteRoleCommand command = new DeleteRoleCommand(roleCode);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, parenteRole.getStatus());
        assertEquals(Status.DELETED, child1.getStatus());
        assertEquals(Status.DELETED, child2.getStatus());
        assertEquals(Status.DELETED, child3.getStatus());
        verify(roleRepository).save(parenteRole);
        verify(roleRepository).save(child1);
        verify(roleRepository).save(child2);
        verify(roleRepository).save(child3);
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsNotRoot() {
        // Given
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_IGRP");
        int roleId = 1;
        String roleCode = "admin";
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);
        role.setDepartment(department);

        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED)).thenReturn(Optional.of(role));

        DeleteRoleCommand command = new DeleteRoleCommand(roleCode);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldHandleNullChildListSafely() {
        // Given
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_IGRP");
        int roleId = 1;
        String roleCode = "admin";
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);
        role.setDepartment(department);

        when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED)).thenReturn(Optional.of(role));

        DeleteRoleCommand command = new DeleteRoleCommand(roleCode);

        // When
        assertDoesNotThrow(() -> underTest.handle(command));

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldSaveOnlyTheDeletedParentRole() {
        // Given

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_IGRP");
        RoleEntity parent = new RoleEntity();
        parent.setId(1);
        parent.setCode("admin");
        parent.setStatus(Status.ACTIVE);
        parent.setParent(null);
        parent.setDepartment(department);

        RoleEntity child = new RoleEntity();
        child.setStatus(Status.ACTIVE);
        child.setParent(parent);
        child.setDepartment(department);

        when(roleRepository.findByCodeAndStatusNot("admin", Status.DELETED)).thenReturn(Optional.of(parent));
        when(roleRepository.save(parent)).thenReturn(parent);
        when(roleRepository.save(child)).thenReturn(child);

        // When
        underTest.handle(new DeleteRoleCommand("admin"));

        // Then
        verify(roleRepository, times(1)).save(parent);
        verify(roleRepository, times(1)).save(child);
    }
}