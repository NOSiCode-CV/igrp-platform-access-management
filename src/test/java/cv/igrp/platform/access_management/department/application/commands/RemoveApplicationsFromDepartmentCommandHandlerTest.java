package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveApplicationsFromDepartmentCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @InjectMocks
    private RemoveApplicationsFromDepartmentCommandHandler handler;

    private DepartmentEntity department;
    private DepartmentEntity childDepartment;
    private ApplicationEntity app1;
    private ApplicationEntity app2;

    @BeforeEach
    void setUp() {
        app1 = new ApplicationEntity();
        app1.setCode("APP1");
        app2 = new ApplicationEntity();
        app2.setCode("APP2");

        childDepartment = new DepartmentEntity();
        childDepartment.setCode("CHILD");
        childDepartment.setApplications(new HashSet<>(Set.of(app1, app2)));
        childDepartment.setChildrenids(new ArrayList<>());

        department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setApplications(new HashSet<>(Set.of(app1, app2)));
        department.setChildrenids(new ArrayList<>(List.of(childDepartment)));
    }

    @Test
    void shouldRemoveApplicationsFromDepartmentAndChildren() {
        // Given
        var command = new RemoveApplicationsFromDepartmentCommand(List.of("APP1", "APP2"), "DEPT");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(departmentRepository.findByCodeAndStatusNotDeleted("CHILD")).thenReturn(childDepartment);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app1);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP2")).thenReturn(app2);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Both department and child should have apps removed
        assertTrue(department.getApplications().isEmpty());
        assertTrue(childDepartment.getApplications().isEmpty());

        // Recursive save is called multiple times (3 per app)
        verify(departmentRepository, atLeast(1)).save(any(DepartmentEntity.class));
        verify(applicationRepository, atLeast(1)).findByCodeAndStatusNotDeleted(anyString());
    }

    @Test
    void shouldHandleDepartmentsWithoutChildren() {
        // Given
        department.setChildrenids(new ArrayList<>());
        var command = new RemoveApplicationsFromDepartmentCommand(List.of("APP1"), "DEPT");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(app1);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(department.getApplications().contains(app1));
        verify(departmentRepository, atLeastOnce()).save(department);
    }

    @Test
    void shouldReturnBadRequest_WhenApplicationDoesNotExist() {
        // Given
        var command = new RemoveApplicationsFromDepartmentCommand(List.of("APP-MISSING"), "DEPT");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP-MISSING")).thenThrow(IgrpResponseStatusException.class);

        // When
        Exception exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Then
        assertNotNull(exception);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void shouldReturnNotFound_WhenDepartmentDoesNotExist() {
        // Given
        var command = new RemoveApplicationsFromDepartmentCommand(List.of("APP1"), "UNKNOWN");
        when(departmentRepository.findByCodeAndStatusNotDeleted("UNKNOWN")).thenThrow(IgrpResponseStatusException.class);

        // When
        Exception exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Then
        assertNotNull(exception);
        verify(applicationRepository, never()).findByCodeAndStatusNotDeleted(anyString());
    }

    @Test
    void shouldIgnoreWhenRemoveListIsEmpty() {
        // Given
        var command = new RemoveApplicationsFromDepartmentCommand(new ArrayList<>(), "DEPT");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(departmentRepository, never()).save(any());
    }
}