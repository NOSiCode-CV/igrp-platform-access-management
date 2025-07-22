package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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
public class CreatePermissionCommandHandlerTest {

    @InjectMocks
    private CreatePermissionCommandHandler underTest;
    @Mock
    private ApplicationEntityRepository applicationRepository;
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
    void itShouldThrowNotFoundException_WhenApplicationDoesNotExist() {
        //... Given
        String applicationCode = "APP";
        String permissionName = "permissionName";
        String permissionDescription = "permissionDescription";
        PermissionDTO permissiondto = new PermissionDTO(null, permissionName, permissionDescription, null, applicationCode);
        CreatePermissionCommand command = new CreatePermissionCommand(permissiondto);

        when(applicationRepository.findByCode(applicationCode))
                .thenReturn(Optional.empty());
        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));
        //... Then

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldCreatePermission_WhenApplicationExists() {
        // Given
        String appCode = "APP";
        String permissionName = "READ_USERS";
        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setApplicationCode(appCode);
        dto.setStatus(Status.ACTIVE);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(1);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setApplication(application);
        permissionToSave.setStatus(Status.ACTIVE);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(10);
        savedPermission.setName(permissionName);
        savedPermission.setApplication(application);
        savedPermission.setStatus(Status.ACTIVE);

        PermissionDTO expectedResponse = new PermissionDTO();
        expectedResponse.setId(10);
        expectedResponse.setName(permissionName);
        expectedResponse.setStatus(Status.ACTIVE);

        // When
        when(applicationRepository.findByCode(appCode)).thenReturn(Optional.of(application));
        when(permissionMapper.mapDtoToEntity(dto, application)).thenReturn(permissionToSave);
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
        assertEquals(appCode, capturedPermission.getApplication().getCode());
        assertEquals(Status.ACTIVE, capturedPermission.getStatus());
    }

    @Test
    void itShouldUseDefaultStatus_WhenStatusIsNotProvided() {
        // Given
        int appId = 1;
        String appCode = "APP";
        String permissionName = "READ_USERS";
        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setApplicationCode(appCode);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(appId);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setApplication(application);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(10);
        savedPermission.setName(permissionName);
        savedPermission.setApplication(application);

        PermissionDTO expectedResponse = new PermissionDTO();
        expectedResponse.setId(10);
        expectedResponse.setName(permissionName);

        // When
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(permissionMapper.mapDtoToEntity(dto, application)).thenReturn(permissionToSave);
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
        assertEquals(appId, capturedPermission.getApplication().getId());
        assertNull(capturedPermission.getStatus());
    }

    @Test
    void itShouldMapSavedPermissionToDTO() {
        // Given
        int appId = 1;
        String appCode = "APP";
        String permissionName = "MANAGE_ROLES";

        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionName);
        dto.setApplicationCode(appCode);

        CreatePermissionCommand command = new CreatePermissionCommand(dto);

        ApplicationEntity application = new ApplicationEntity();
        application.setId(appId);

        PermissionEntity permissionToSave = new PermissionEntity();
        permissionToSave.setName(permissionName);
        permissionToSave.setApplication(application);

        PermissionEntity savedPermission = new PermissionEntity();
        savedPermission.setId(20);
        savedPermission.setName(permissionName);
        savedPermission.setApplication(application);

        PermissionDTO mappedDTO = new PermissionDTO();
        mappedDTO.setId(20);
        mappedDTO.setName(permissionName);

        // When
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(application));
        when(permissionMapper.mapDtoToEntity(dto, application)).thenReturn(permissionToSave);
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