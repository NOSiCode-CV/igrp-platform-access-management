package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoveDepartmentsFromMenuCommandHandlerTest {

    @InjectMocks
    private RemoveDepartmentsFromMenuCommandHandler handler;

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private DepartmentEntityRepository departmentEntityRepository;

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    private MenuEntryEntity menuEntry;
    private DepartmentEntity department1;
    private DepartmentEntity department2;
    private ApplicationEntity application;
    private MenuEntryDTO menuEntryDTO;

    @BeforeEach
    void setUp() {
        // Initialize mock entities and DTOs
        department1 = new DepartmentEntity();
        department1.setId(100);
        department1.setCode("DEPT_A");
        department1.setName("Department A");
        department1.setStatus(DepartmentStatus.ACTIVE);

        department2 = new DepartmentEntity();
        department2.setId(200);
        department2.setCode("DEPT_B");
        department2.setName("Department B");
        department2.setStatus(DepartmentStatus.ACTIVE);

        application = new ApplicationEntity();
        application.setCode("APP_A");
        application.setName("Application A");
        application.setStatus(Status.ACTIVE);

        menuEntry = new MenuEntryEntity();
        menuEntry.setId(1);
        menuEntry.setCode("MENU_APP");
        menuEntry.setName("Application Menu");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.getDepartments().addAll(new HashSet<>(List.of(department1, department2)));

        menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(1);
        menuEntryDTO.setCode("MENU_APP");
        menuEntryDTO.setName("Application Menu");
        menuEntryDTO.setStatus(Status.ACTIVE);
    }

    @Test
    void testHandle_RemovesDepartmentsFromMenuSuccessfully() {
        // Given a command to remove departments
        List<String> departmentCodesToRemove = List.of("DEPT_A", "DEPT_B");
        RemoveDepartmentsFromMenuCommand command = new RemoveDepartmentsFromMenuCommand(departmentCodesToRemove, "APP_A", "MENU_APP");

        // Mock repository and mapper behavior
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(application.getCode())).thenReturn(application);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getCode(), Status.DELETED))
                .thenReturn(Optional.of(menuEntry));
        when(departmentEntityRepository.findByCodeAndStatusNot("DEPT_A", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department1));
        when(departmentEntityRepository.findByCodeAndStatusNot("DEPT_B", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department2));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When the handler is called
        ResponseEntity<MenuEntryDTO> response = handler.handle(command);

        // Then verify the response and interactions
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(menuEntryDTO.getId(), response.getBody().getId());
        assertEquals(0, menuEntry.getDepartments().size());
        verify(menuEntryRepository).save(menuEntry);
    }

    @Test
    void testHandle_DepartmentsAlreadyRemoved_RemovesOnlyRemaining() {
        // Given a menu with one department already removed from the command list
        menuEntry.getDepartments().clear();
        menuEntry.getDepartments().add(department1);
        List<String> departmentCodesToRemove = List.of("DEPT_A", "DEPT_B");
        RemoveDepartmentsFromMenuCommand command = new RemoveDepartmentsFromMenuCommand(departmentCodesToRemove, "APP_A", "MENU_APP");

        // Mock repository and mapper behavior
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(application.getCode())).thenReturn(application);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getCode(), Status.DELETED))
                .thenReturn(Optional.of(menuEntry));
        when(departmentEntityRepository.findByCodeAndStatusNot("DEPT_A", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department1));
        when(departmentEntityRepository.findByCodeAndStatusNot("DEPT_B", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department2));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When the handler is called
        ResponseEntity<MenuEntryDTO> response = handler.handle(command);

        // Then verify the response and interactions
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(menuEntryDTO.getId(), response.getBody().getId());
        assertEquals(0, menuEntry.getDepartments().size());
        verify(menuEntryRepository).save(menuEntry);
    }

    @Test
    void testHandle_MenuNotFound_ThrowsException() {
        // Given a command for a non-existent menu
        String nonExistentMenuCode = "NON_EXISTENT_MENU";
        RemoveDepartmentsFromMenuCommand command = new RemoveDepartmentsFromMenuCommand(List.of("DEPT_A"), "APP_A", nonExistentMenuCode);

        // Mock repository to return empty optional for non-existent menu
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(application.getCode())).thenReturn(application);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, nonExistentMenuCode, Status.DELETED))
                .thenReturn(Optional.empty());

        // When and Then: Verify exception is thrown and check its properties
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertNotNull(exception.getBody().getProperties());
        assertEquals("Menu not found with code: " + nonExistentMenuCode, exception.getBody().getProperties().get("details"));
    }

    @Test
    void testHandle_DepartmentNotFound_ThrowsException() {
        // Given a command with a non-existent department ID
        String nonExistentDepartmentCode = "NON_EXISTENT_DEPT";
        List<String> departmentCodes = List.of("DEPT_A", nonExistentDepartmentCode);
        RemoveDepartmentsFromMenuCommand command = new RemoveDepartmentsFromMenuCommand(departmentCodes, "APP_A", "MENU_APP");

        // Mock repository behavior for non-existent department
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(application.getCode())).thenReturn(application);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(application, command.getCode(), Status.DELETED))
                .thenReturn(Optional.of(menuEntry));
        when(departmentEntityRepository.findByCodeAndStatusNot("DEPT_A", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department1));
        when(departmentEntityRepository.findByCodeAndStatusNot(nonExistentDepartmentCode, DepartmentStatus.DELETED))
                .thenReturn(Optional.empty());

        // When and Then: Verify exception is thrown and check its properties
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        assertNotNull(exception.getBody().getProperties());
        assertEquals("Department not found with id: " + nonExistentDepartmentCode, exception.getBody().getProperties().get("details"));
    }
}