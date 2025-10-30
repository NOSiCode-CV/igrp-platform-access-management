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
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AddApplicationsToDepartmentCommandHandler.
 * <p>
 * These tests cover:
 *  - Successful addition of applications to a department.
 *  - Error when parent department does not exist.
 *  - Forbidden error when parent department lacks required applications.
 *  - Validation that repository methods are called correctly.
 */
@ExtendWith(MockitoExtension.class)
class AddApplicationsToDepartmentCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @InjectMocks
    private AddApplicationsToDepartmentCommandHandler handler;

    private DepartmentEntity department;
    private ApplicationEntity application;
    private AddApplicationsToDepartmentCommand command;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setApplications(new HashSet<>());

        application = new ApplicationEntity();
        application.setCode("APP1");

        command = new AddApplicationsToDepartmentCommand(List.of("APP1"), "DEPT");
    }

    @Test
    void testHandle_ShouldAddApplicationsToDepartmentSuccessfully_WhenNoParentDepartment() {
        // Arrange
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(application);

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertTrue(department.getApplications().contains(application));
        verify(departmentRepository, times(1)).save(department);
    }

    @Test
    void testHandle_ShouldThrowNotFound_WhenParentDepartmentMissingInRepository() {
        // Arrange
        DepartmentEntity parent = new DepartmentEntity();
        parent.setCode("PARENT");
        parent.setId(1);

        department.setParentId(parent);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(application);
        when(departmentRepository.findById(parent.getId())).thenReturn(Optional.empty());

        // Act + Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertNotNull(ex.getBody().getTitle());
        assertEquals("Parent Department was not found: PARENT", ex.getBody().getTitle());
        assertEquals(404, ex.getStatusCode().value());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void testHandle_ShouldThrowForbidden_WhenParentDoesNotHaveApplication() {
        // Arrange
        DepartmentEntity parent = new DepartmentEntity();
        parent.setCode("PARENT");
        parent.setId(1);
        parent.setApplications(new HashSet<>()); // No apps assigned

        department.setParentId(parent);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(application);
        when(departmentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        // Act + Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertNotNull(ex.getBody().getTitle());
        assertTrue(ex.getBody().getTitle().contains("Cannot associate department 'DEPT'"));
        assertEquals(403, ex.getStatusCode().value());
        verify(departmentRepository, never()).save(any());
    }

    @Test
    void testHandle_ShouldAddApplication_WhenParentHasIt() {
        // Arrange
        DepartmentEntity parent = new DepartmentEntity();
        parent.setCode("PARENT");
        parent.setId(1);

        ApplicationEntity parentApp = new ApplicationEntity();
        parentApp.setCode("APP1");
        parent.setApplications(new HashSet<>(Collections.singleton(parentApp)));

        department.setParentId(parent);

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(application);
        when(departmentRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertTrue(department.getApplications().contains(application));
        verify(departmentRepository, times(1)).save(department);
    }

    @Test
    void testHandle_ShouldAddMultipleApplications() {
        // Arrange
        ApplicationEntity app2 = new ApplicationEntity();
        app2.setCode("APP2");

        AddApplicationsToDepartmentCommand multiAppCommand =
                new AddApplicationsToDepartmentCommand( List.of("APP1", "APP2"), "DEPT");

        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT")).thenReturn(department);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1")).thenReturn(application);
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP2")).thenReturn(app2);

        // Act
        ResponseEntity<String> response = handler.handle(multiAppCommand);

        // Assert
        assertEquals(204, response.getStatusCode().value());
        assertTrue(department.getApplications().containsAll(List.of(application, app2)));
        verify(departmentRepository, times(2)).save(department);
    }
}