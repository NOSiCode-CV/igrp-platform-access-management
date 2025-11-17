package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
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
public class AddPermissionsToMenuCommandHandlerTest {

    @InjectMocks
    private AddPermissionsToMenuCommandHandler addPermissionsToMenuCommandHandler;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private DepartmentEntityRepository departmentEntityRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(addPermissionsToMenuCommandHandler);
    }

    @Test
    void itShouldThrowException_WhenGivenMenuEntry_NotFound() {
        // Given
        String menuEntryCode = "admin";
        String appCode = "APP";
        String deptCode = "DEPT";
        ArrayList<String> roleList = new ArrayList<>();
        roleList.add("test_role");
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(roleList, appCode, deptCode, menuEntryCode);

        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setName("test_role");
        role.setStatus(Status.ACTIVE);

        ApplicationEntity app = new ApplicationEntity();
        app.setId(1);
        app.setCode(appCode);
        app.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentEntityRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode))
                .thenReturn(app);

        when(roleRepository.findAllByDepartmentAndCodeIn(department, roleList))
                .thenReturn(List.of(role));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED))
                .thenReturn(Optional.empty());

        // When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> addPermissionsToMenuCommandHandler.handle(command));

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldThrowException_When_NoRole_Is_Found() {
        // Given
        String menuEntryCode = "admin";
        String roleName = "test";
        String appCode = "APP";
        String deptCode = "DEPT";
        ArrayList<String> roleList = new ArrayList<>();
        roleList.add(roleName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(roleList, appCode, deptCode, menuEntryCode);

        ArrayList<RoleEntity> savedRoles = new ArrayList<>();

        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setName(roleName);
        role.setStatus(Status.ACTIVE);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(roleRepository.findAllByDepartmentAndCodeIn(department, roleList))
                .thenReturn(savedRoles);

        // When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> addPermissionsToMenuCommandHandler.handle(command));

        // Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldAddRolesToMenuEntry_When_MenuEntryIsFound_AndRole_IsAvailable() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer activeRoleId = 1;
        Integer deletedRoleId = 2;
        String activeRoleName = "test_active";
        String deletedRoleName = "test_deleted";
        String appCode = "APP";
        String deptCode = "DEPT";
        List<String> roleNames = List.of(activeRoleName, deletedRoleName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(roleNames, appCode, deptCode, menuEntryCode);

        ApplicationEntity app = new ApplicationEntity();
        app.setId(1);
        app.setCode(appCode);
        app.setStatus(Status.ACTIVE);

        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(activeRoleId);
        activeRole.setStatus(Status.ACTIVE);
        activeRole.setName(activeRoleName);

        RoleEntity deletedRole = new RoleEntity();
        deletedRole.setId(deletedRoleId);
        deletedRole.setStatus(Status.DELETED);
        deletedRole.setName(deletedRoleName);

        List<RoleEntity> returnedRoles = List.of(activeRole);

        MenuEntryEntity menuEntry = new MenuEntryEntity();
        menuEntry.setId(menuEntryId);
        menuEntry.setCode(menuEntryCode);
        menuEntry.setName("Test MenuEntry");
        menuEntry.setStatus(Status.ACTIVE);
        menuEntry.setRoles(new HashSet<>());

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        List<String> roleList = new ArrayList<>();
        roleList.add(activeRoleName);
        menuEntryDTO.setRoles(roleList);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentEntityRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode))
                .thenReturn(app);

        when(roleRepository.findAllByDepartmentAndCodeIn(department, roleNames)).thenReturn(returnedRoles);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);
        when(menuEntryMapper.toDTO(menuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(menuEntryId, result.getBody().getId());
        assertEquals(1, result.getBody().getRoles().size());
        assertTrue(result.getBody().getRoles().contains(activeRoleName));

        verify(menuEntryRepository).save(menuEntry);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verify(menuEntryMapper, times(1)).toDTO(menuEntry);

        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    void itShouldIgnoreRoles_WithDeletedStatus_WhenAddingToAMenuEntry() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer activeRoleId = 1;
        Integer deletedRoleId = 2;
        String activeRoleName = "test_active";
        String deletedRoleName = "test_deleted";
        String appCode = "APP";
        String deptCode = "DEPT";
        List<String> roleList = List.of(activeRoleName, deletedRoleName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(roleList, appCode, deptCode, menuEntryCode);

        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(activeRoleId);
        activeRole.setStatus(Status.ACTIVE);
        activeRole.setName(activeRoleName);
        String activeRoleDesc = "Active Role";
        activeRole.setDescription(activeRoleDesc);

        ApplicationEntity app = new ApplicationEntity();
        app.setId(1);
        app.setCode(appCode);
        app.setStatus(Status.ACTIVE);

        List<RoleEntity> savedRoles = List.of(activeRole);

        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setId(menuEntryId);
        savedMenuEntry.setCode(menuEntryCode);
        savedMenuEntry.setName("MenuEntry Name");
        savedMenuEntry.setStatus(Status.ACTIVE);
        savedMenuEntry.setRoles(new HashSet<>());

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        List<String> roleNames = new ArrayList<>();
        roleNames.add(activeRoleName);
        menuEntryDTO.setRoles(roleNames);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentEntityRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(roleRepository.findAllByDepartmentAndCodeIn(department, roleNames)).thenReturn(savedRoles);
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(menuEntryMapper.toDTO(savedMenuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(menuEntryId, result.getBody().getId());
        assertEquals(1, result.getBody().getRoles().size());
        assertTrue(result.getBody().getRoles().contains(activeRoleName));

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryRepository, times(1)).save(savedMenuEntry);
        verify(menuEntryMapper, times(1)).toDTO(savedMenuEntry);

        verifyNoMoreInteractions(menuEntryMapper);
    }

    @Test
    void itShouldNotDuplicateRoles_WhenRoleAlreadyExistsInMenuEntry() {
        // Given
        int menuEntryId = 1;
        String menuEntryCode = "admin";
        Integer roleId = 1;
        String roleName = "role1";
        String appCode = "APP";
        String deptCode = "DEPT";

        List<String> roleNames = List.of(roleName);
        AddPermissionsToMenuCommand command = new AddPermissionsToMenuCommand(roleNames, appCode, deptCode, menuEntryCode);

        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(roleId);
        activeRole.setName(roleName);
        activeRole.setStatus(Status.ACTIVE);

        ApplicationEntity app = new ApplicationEntity();
        app.setId(1);
        app.setCode("APP");
        app.setStatus(Status.ACTIVE);

        MenuEntryEntity savedMenuEntry = new MenuEntryEntity();
        savedMenuEntry.setId(menuEntryId);
        savedMenuEntry.setCode(menuEntryCode);
        savedMenuEntry.setRoles(new HashSet<>(Set.of(activeRole)));

        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setId(menuEntryId);
        menuEntryDTO.setCode(menuEntryCode);
        List<String> roleList = new ArrayList<>();
        roleList.add(roleName);
        menuEntryDTO.setRoles(roleList);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentEntityRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(app);
        when(roleRepository.findAllByDepartmentAndCodeIn(department, roleNames)).thenReturn(List.of(activeRole));
        when(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryCode, Status.DELETED)).thenReturn(Optional.of(savedMenuEntry));
        when(menuEntryRepository.save(savedMenuEntry)).thenReturn(savedMenuEntry);
        when(menuEntryMapper.toDTO(savedMenuEntry)).thenReturn(menuEntryDTO);

        // When
        ResponseEntity<MenuEntryDTO> result = addPermissionsToMenuCommandHandler.handle(command);

        // Then
        assertNotNull(result.getBody());
        assertEquals(menuEntryId, result.getBody().getId());
        assertEquals(1, result.getBody().getRoles().size());
        assertTrue(result.getBody().getRoles().contains(roleName));
        assertEquals(1, savedMenuEntry.getRoles().size());

        verify(menuEntryRepository).save(savedMenuEntry);
        verify(menuEntryRepository, times(1)).save(savedMenuEntry);
        verify(menuEntryMapper, times(1)).toDTO(savedMenuEntry);

        verifyNoMoreInteractions(menuEntryMapper);
    }
}
