package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
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

@ExtendWith(MockitoExtension.class)
class AddMenusToDepartmentCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @InjectMocks
    private AddMenusToDepartmentCommandHandler handler;

    private DepartmentEntity department;
    private ApplicationEntity application;
    private MenuEntryEntity menu;
    private AddMenusToDepartmentCommand command;

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setCode("DEPT");
        department.setStatus(DepartmentStatus.ACTIVE);
        department.setMenuentries(new HashSet<>());

        application = new ApplicationEntity();
        application.setCode("APPLICATION");
        application.setStatus(Status.ACTIVE);

        menu = new MenuEntryEntity();
        menu.setCode("MENU1");
        menu.setStatus(Status.ACTIVE);
        menu.setDepartments(new HashSet<>());

        command = new AddMenusToDepartmentCommand(List.of("MENU1"), "DEPT", "APPLICATION");
    }

    @Test
    void testHandle_ShouldAddMenuToDepartmentSuccessfully_WhenNoParentDepartment() {
        // Arrange
        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));
        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED))).thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(eq(application), eq("MENU1"), eq(Status.DELETED)))
                .thenReturn(Optional.of(menu));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertTrue(menu.getDepartments().contains(department));
        verify(menuEntryRepository, times(1)).save(menu);
    }

    @Test
    void testHandle_ShouldThrowNotFound_WhenDepartmentDoesNotExist() {
        // Arrange
        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals("Department not found", ex.getBody().getTitle());
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(menuEntryRepository);
    }

    @Test
    void testHandle_ShouldThrowNotFound_WhenApplicationDoesNotExist() {

        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));

        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED)))
        .thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals("Application not found", ex.getBody().getTitle());
        assertEquals(404, ex.getStatusCode().value());
        verifyNoInteractions(menuEntryRepository);
    }

    @Test
    void testHandle_ShouldThrowNotFound_WhenMenuEntryDoesNotExist() {
        // Arrange
        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));
        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED)))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(eq(application), eq("MENU1"), eq(Status.DELETED)))
                .thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals("Menu Entry not found", ex.getBody().getTitle());
        assertEquals(404, ex.getStatusCode().value());
        verify(menuEntryRepository, never()).save(any());
    }

    @Test
    void testHandle_ShouldThrowBadRequest_WhenParentDepartmentDoesNotHaveMenu() {
        // Arrange
        DepartmentEntity parent = new DepartmentEntity();
        parent.setCode("PARENT");
        parent.setMenuentries(new HashSet<>()); // Parent has no menus
        department.setParentId(parent);

        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));
        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED)))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(eq(application), eq("MENU1"), eq(Status.DELETED)))
                .thenReturn(Optional.of(menu));
        when(departmentRepository.findById(eq(parent.getId()))).thenReturn(Optional.of(parent));

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        assertEquals("Invalid Department Association", ex.getBody().getTitle());
        assertEquals(400, ex.getStatusCode().value());
        verify(menuEntryRepository, never()).save(any());
    }

    @Test
    void testHandle_ShouldAddMenuToDepartment_WhenParentHasMenu() {
        // Arrange
        DepartmentEntity parent = new DepartmentEntity();
        parent.setCode("PARENT");

        MenuEntryEntity parentMenu = new MenuEntryEntity();
        parentMenu.setCode("MENU1");
        parent.setMenuentries(new HashSet<>(Collections.singleton(parentMenu)));

        department.setParentId(parent);

        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));
        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED)))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(eq(application), eq("MENU1"), eq(Status.DELETED)))
                .thenReturn(Optional.of(menu));
        when(departmentRepository.findById(eq(parent.getId()))).thenReturn(Optional.of(parent));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        assertTrue(menu.getDepartments().contains(department));
        verify(menuEntryRepository, times(1)).save(menu);
    }

    @Test
    void testHandle_ShouldNotSave_WhenMenuAlreadyAssociated() {
        // Arrange
        menu.getDepartments().add(department);

        when(departmentRepository.findByCodeAndStatusNot(eq("DEPT"), eq(DepartmentStatus.DELETED)))
                .thenReturn(Optional.of(department));
        when(applicationRepository.findByCodeAndStatusNot(eq("APPLICATION"), eq(Status.DELETED)))
                .thenReturn(Optional.of(application));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(eq(application), eq("MENU1"), eq(Status.DELETED)))
                .thenReturn(Optional.of(menu));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(204, response.getStatusCode().value());
        verify(menuEntryRepository, never()).save(any());
    }
}