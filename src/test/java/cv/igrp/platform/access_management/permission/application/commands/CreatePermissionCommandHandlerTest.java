package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Disabled
public class CreatePermissionCommandHandlerTest {

    @InjectMocks
    private CreatePermissionCommandHandler underTest;
    @Mock
    private DepartmentEntityRepository departmentRepository;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private PermissionMapper permissionMapper;
    @Captor
    private ArgumentCaptor<PermissionEntity> permissionCaptor;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowNotFoundException_WhenDepartmentDoesNotExist() {
        //... Given
        String departmentCode = "DEPT";
        String permissionName = "permissionName";
        String permissionDescription = "permissionDescription";
        PermissionDTO permissiondto = new PermissionDTO(null, permissionName, permissionDescription, null, departmentCode);
        CreatePermissionCommand command = new CreatePermissionCommand(permissiondto);

        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED))
                .thenReturn(Optional.empty());
        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));
        //... Then

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldCreatePermission_WhenDepartmentExists() {
        // Given
        String departmentCode = "DEPT";
        String permissionName = "READ_USERS";
        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setDepartmentCode(departmentCode);
        dto.setStatus(Status.ACTIVE);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(1);
        department.setCode(departmentCode);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setDepartment(department);
        permissionToSave.setStatus(Status.ACTIVE);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(10);
        savedPermission.setName(permissionName);
        savedPermission.setDepartment(department);
        savedPermission.setStatus(Status.ACTIVE);

        PermissionDTO expectedResponse = new PermissionDTO();
        expectedResponse.setId(10);
        expectedResponse.setName(permissionName);
        expectedResponse.setStatus(Status.ACTIVE);

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(permissionMapper.mapDtoToEntity(dto, department)).thenReturn(permissionToSave);
        when(permissionRepository.save(permissionToSave)).thenReturn(savedPermission);
        when(permissionMapper.mapToDTO(savedPermission)).thenReturn(expectedResponse);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity capturedPermission = permissionCaptor.getValue();

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        assertEquals(permissionName, capturedPermission.getName());
        assertEquals(departmentCode, capturedPermission.getDepartment().getCode());
        assertEquals(Status.ACTIVE, capturedPermission.getStatus());
    }

    @Test
    void itShouldUseDefaultStatus_WhenStatusIsNotProvided() {
        // Given
        int deptId = 1;
        String departmentCode = "DEPT";
        String permissionName = "READ_USERS";
        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setDepartmentCode(departmentCode);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(deptId);
        department.setCode(departmentCode);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setDepartment(department);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(10);
        savedPermission.setName(permissionName);
        savedPermission.setDepartment(department);

        PermissionDTO expectedResponse = new PermissionDTO();
        expectedResponse.setId(10);
        expectedResponse.setName(permissionName);

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(permissionMapper.mapDtoToEntity(dto, department)).thenReturn(permissionToSave);
        when(permissionRepository.save(permissionToSave)).thenReturn(savedPermission);
        when(permissionMapper.mapToDTO(savedPermission)).thenReturn(expectedResponse);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity capturedPermission = permissionCaptor.getValue();

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        assertEquals(permissionName, capturedPermission.getName());
        assertEquals(deptId, capturedPermission.getDepartment().getId());
        assertNull(capturedPermission.getStatus());
    }

    @Test
    void itShouldMapSavedPermissionToDTO() {
        // Given
        int deptId = 1;
        String departmentCode = "DEPT";
        String permissionName = "MANAGE_ROLES";

        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setDepartmentCode(departmentCode);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        DepartmentEntity department = new DepartmentEntity();
        department.setId(deptId);
        department.setCode(departmentCode);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setDepartment(department);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(20);
        savedPermission.setName(permissionName);
        savedPermission.setDepartment(department);

        PermissionDTO mappedDTO = new PermissionDTO();
        mappedDTO.setId(20);
        mappedDTO.setName(permissionName);

        // When
        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(permissionMapper.mapDtoToEntity(dto, department)).thenReturn(permissionToSave);
        when(permissionRepository.save(permissionToSave)).thenReturn(savedPermission);
        when(permissionMapper.mapToDTO(savedPermission)).thenReturn(mappedDTO);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionMapper).mapToDTO(savedPermission);
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(mappedDTO, response.getBody());
    }
}