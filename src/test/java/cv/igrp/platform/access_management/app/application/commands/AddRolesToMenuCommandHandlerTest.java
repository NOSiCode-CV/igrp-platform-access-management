package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRolesToMenuCommandHandlerTest {

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private AddRolesToMenuCommandHandler handler;

    private DepartmentEntity department;
    private ApplicationEntity app;
    private MenuEntryEntity menu;
    private MenuEntryDTO menuDTO;

    private RoleEntity roleA;
    private RoleEntity roleB;

    @BeforeEach
    void setup() {

        department = new DepartmentEntity();
        department.setId(10);
        department.setCode("HR");

        app = new ApplicationEntity();
        app.setId(99);
        app.setCode("APP");

        menu = new MenuEntryEntity();
        menu.setId(5);
        menu.setCode("MENU1");
        menu.setStatus(Status.ACTIVE);
        menu.setRoles(new HashSet<>());

        menuDTO = new MenuEntryDTO();
        menuDTO.setId(5);
        menuDTO.setCode("MENU1");

        roleA = new RoleEntity();
        roleA.setId(1);
        roleA.setCode("ADMIN");
        roleA.setStatus(Status.ACTIVE);

        roleB = new RoleEntity();
        roleB.setId(2);
        roleB.setCode("MANAGER");
        roleB.setStatus(Status.ACTIVE);
    }

    // ----------------------------------------------------------
    // SUCCESS SCENARIOS
    // ----------------------------------------------------------

    @Test
    @DisplayName("Should successfully add roles to menu")
    void testAddRoles_success() {

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN", "MANAGER"),
                "HR",
                "APP",
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenReturn(department);

        when(roleRepository.findAllByDepartmentAndCodeInNotDeleted(eq(department), any(), eq(Status.DELETED)))
                .thenReturn(List.of(roleA, roleB));

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP"))
                .thenReturn(app);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menu));

        when(menuEntryRepository.save(menu)).thenReturn(menu);
        when(menuEntryMapper.toDTO(menu)).thenReturn(menuDTO);

        ResponseEntity<MenuEntryDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(menuDTO, response.getBody());

        assertEquals(2, menu.getRoles().size());
    }

    @Test
    @DisplayName("Should add only non-deleted roles from the input list")
    void testAddRoles_filtersDeletedRoles() {

        roleB.setStatus(Status.DELETED); // deleted role – must be ignored

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN", "MANAGER"),
                "HR",
                "APP",
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenReturn(department);

        when(roleRepository.findAllByDepartmentAndCodeInNotDeleted(eq(department), any(), eq(Status.DELETED)))
                .thenReturn(List.of(roleA, roleB));

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP"))
                .thenReturn(app);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menu));

        when(menuEntryRepository.save(menu)).thenReturn(menu);
        when(menuEntryMapper.toDTO(menu)).thenReturn(menuDTO);

        ResponseEntity<MenuEntryDTO> response = handler.handle(command);

        assertEquals(1, menu.getRoles().size());
        assertTrue(menu.getRoles().contains(roleA));
        assertFalse(menu.getRoles().contains(roleB));
    }

    @Test
    @DisplayName("Should succeed even when optional fields are null")
    void testAddRoles_optionalValuesNull() {

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN"),
                "HR",
        null,         // app code null
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenReturn(department);

        when(roleRepository.findAllByDepartmentAndCodeInNotDeleted(eq(department), any(), eq(Status.DELETED)))
                .thenReturn(List.of(roleA));

        when(applicationRepository.findByCodeAndStatusNotDeleted(null))
                .thenReturn(app);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, "MENU1", Status.DELETED))
                .thenReturn(Optional.of(menu));

        when(menuEntryRepository.save(menu)).thenReturn(menu);
        when(menuEntryMapper.toDTO(menu)).thenReturn(menuDTO);

        ResponseEntity<MenuEntryDTO> response = handler.handle(command);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    // ----------------------------------------------------------
    // ERROR SCENARIOS
    // ----------------------------------------------------------

    @Test
    @DisplayName("Should throw NOT_FOUND when no valid roles returned")
    void testAddRoles_rolesNotFound() {

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN"),
                "HR",
                "APP",
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenReturn(department);

        when(roleRepository.findAllByDepartmentAndCodeInNotDeleted(eq(department), any(), eq(Status.DELETED)))
                .thenReturn(List.of()); // no roles found

        assertThrows(
                IgrpResponseStatusException.class,
                () -> handler.handle(command)
        );
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when menu entry is missing")
    void testAddRoles_menuNotFound() {

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN"),
                "HR",
                "APP",
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenReturn(department);

        when(roleRepository.findAllByDepartmentAndCodeInNotDeleted(eq(department), any(), eq(Status.DELETED)))
                .thenReturn(List.of(roleA));

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP"))
                .thenReturn(app);

        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, "MENU1", Status.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(
                IgrpResponseStatusException.class,
                () -> handler.handle(command)
        );
    }

    @Test
    @DisplayName("Should throw NOT_FOUND when department is missing")
    void testAddRoles_departmentNotFound() {

        AddRolesToMenuCommand command = new AddRolesToMenuCommand(
                List.of("ADMIN"),
                "HR",
                "APP",
                "MENU1"
        );

        when(departmentRepository.findByCodeAndStatusNotDeleted("HR"))
                .thenThrow(IgrpResponseStatusException.class);

        assertThrows(
                IgrpResponseStatusException.class, // same behavior handler currently causes
                () -> handler.handle(command)
        );
    }
}