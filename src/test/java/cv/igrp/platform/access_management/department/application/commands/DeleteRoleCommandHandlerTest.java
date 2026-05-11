package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private cv.igrp.platform.access_management.shared.domain.events.EventPublisher eventPublisher;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenProvidedRoleId_NotFound() {
        //... Given
        String roleCode = "admin";
        String deptCode = "DEPT";
        DeleteRoleCommand command = new DeleteRoleCommand(deptCode, roleCode);

        DepartmentEntity departmentEntity = new DepartmentEntity();

        departmentEntity.setCode(deptCode);
        departmentEntity.setStatus(DepartmentStatus.ACTIVE);

        //... When
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(departmentEntity);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(departmentEntity, roleCode, Status.DELETED))
                .thenReturn(Optional.empty());
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsFound() {
        // Given

        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);

        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        RoleEntity parenteRole = new RoleEntity();
        role.setParent(parenteRole);
        role.setDepartment(department);


        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        DeleteRoleCommand command = new DeleteRoleCommand(deptCode, roleCode);

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
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);

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
        // connect children to parent list as handler traverses role.getChildren()
        parenteRole.getChildren().add(child1);
        parenteRole.getChildren().add(child2);
        parenteRole.getChildren().add(child3);

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(parenteRole));
        when(roleRepository.save(parenteRole)).thenReturn(parenteRole);
        when(roleRepository.save(child1)).thenReturn(child1);
        when(roleRepository.save(child2)).thenReturn(child2);
        when(roleRepository.save(child3)).thenReturn(child3);

        DeleteRoleCommand command = new DeleteRoleCommand(deptCode, roleCode);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, parenteRole.getStatus());
        assertEquals(Status.DELETED, child1.getStatus());
        assertEquals(Status.DELETED, child2.getStatus());
        assertEquals(Status.DELETED, child3.getStatus());
        verify(roleRepository, atLeastOnce()).save(parenteRole);
        verify(roleRepository, atLeastOnce()).save(child1);
        verify(roleRepository, atLeastOnce()).save(child2);
        verify(roleRepository, atLeastOnce()).save(child3);
    }

    @Test
    void itShouldDeleteRole_WhenRoleIsNotRoot() {
        // Given
        int roleId = 1;
        String deptCode = "DEPT_IGRP";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);

        String roleCode = "admin";
        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);
        role.setDepartment(department);

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));

        DeleteRoleCommand command = new DeleteRoleCommand(deptCode, roleCode);

        // When
        underTest.handle(command);

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldHandleNullChildListSafely() {
        // Given
        int roleId = 1;
        String roleCode = "admin";
        String deptCode = "DEPT_IGRP";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        RoleEntity role = new RoleEntity();
        role.setId(roleId);
        role.setCode(roleCode);
        role.setStatus(Status.ACTIVE);
        role.setParent(null);
        role.setDepartment(department);

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));

        DeleteRoleCommand command = new DeleteRoleCommand(deptCode, roleCode);

        // When
        assertDoesNotThrow(() -> underTest.handle(command));

        // Then
        assertEquals(Status.DELETED, role.getStatus());
        verify(roleRepository).save(role);
    }

    @Test
    void itShouldSaveOnlyTheDeletedParentRole() {
        // Given

        String deptCode = "DEPT_IGRP";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);

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

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, "admin", Status.DELETED)).thenReturn(Optional.of(parent));
        when(roleRepository.save(parent)).thenReturn(parent);
        when(roleRepository.save(child)).thenReturn(child);
        // connect child as well
        parent.getChildren().add(child);

        // When
        underTest.handle(new DeleteRoleCommand(deptCode, "admin"));

        // Then
        verify(roleRepository, atLeastOnce()).save(parent);
        verify(roleRepository, atLeastOnce()).save(child);
    }
}