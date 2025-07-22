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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdatePermissionCommandHandlerTest {

    @InjectMocks
    private UpdatePermissionCommandHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private ApplicationEntityRepository applicationRepository;
    @Mock
    private PermissionMapper permissionMapper;
    @Captor
    private ArgumentCaptor<PermissionEntity> permissionCaptor;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFound_When_ProvidedPermissionId_NotFound() {
        //... Given
        int permissionId = 100;
        String permissionName = "manage";
        String permissionNewDescription = "PermissionNewId";

        PermissionDTO permissionNewData = new PermissionDTO();

        permissionNewData.setDescription(permissionNewDescription);
        permissionNewData.setStatus(Status.INACTIVE);
        UpdatePermissionCommand command = new UpdatePermissionCommand(permissionNewData, permissionName);

        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldThrowRecordNotFound_When_ProvidedApplicationId_NotFound() {
        //... Given
        int permissionId = 100;
        String permissionName = "manage";
        String applicationCode = "APP";
        String permissionNewDescription = "PermissionNewId";
        String permissionPreviousName = "Permission PreviousName";

        PermissionDTO permissionNewData = new PermissionDTO();

        permissionNewData.setDescription(permissionNewDescription);
        permissionNewData.setStatus(Status.INACTIVE);
        permissionNewData.setApplicationCode(applicationCode);
        UpdatePermissionCommand command = new UpdatePermissionCommand(permissionNewData, permissionName);

        PermissionEntity foundPermission = new PermissionEntity();
        foundPermission.setId(permissionId);
        foundPermission.setDescription(permissionPreviousName);

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
                .thenReturn(Optional.of(foundPermission));
        when(applicationRepository.findByCode(applicationCode))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldUpdateApplication_WhenProvidedApplicationIdIsDifferent() {
        // Given
        int permissionId = 1;
        String permissionName = "manage";
        int previousAppId = 10;
        String previousAppCode = "PREV_APP";
        int newAppId = 20;
        String newAppCode = "APP";

        ApplicationEntity previousApp = new ApplicationEntity();
        previousApp.setId(previousAppId);
        previousApp.setCode(previousAppCode);

        ApplicationEntity newApp = new ApplicationEntity();
        newApp.setId(newAppId);
        newApp.setCode(newAppCode);

        PermissionEntity existingPermission = new PermissionEntity();
        existingPermission.setId(permissionId);
        existingPermission.setName("READ_DATA");
        existingPermission.setStatus(Status.ACTIVE);
        existingPermission.setApplication(previousApp);

        PermissionDTO updateDTO = new PermissionDTO();
        updateDTO.setApplicationCode(newAppCode);
        updateDTO.setName("READ_DATA");
        updateDTO.setDescription("Updated Description");
        updateDTO.setStatus(Status.ACTIVE);

        UpdatePermissionCommand command = new UpdatePermissionCommand(updateDTO, permissionName);

        PermissionEntity savedPermission = new PermissionEntity();
        PermissionDTO mappedDTO = new PermissionDTO();

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED)).thenReturn(Optional.of(existingPermission));
        when(applicationRepository.findByCode(newAppCode)).thenReturn(Optional.of(newApp));
        when(permissionRepository.save(any(PermissionEntity.class))).thenReturn(savedPermission);
        when(permissionMapper.mapToDTO(savedPermission)).thenReturn(mappedDTO);

        // When
        underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity capturedPermission = permissionCaptor.getValue();

        assertEquals(newAppId, capturedPermission.getApplication().getId());
    }

    @Test
    void itShouldUpdatePermission_WhenValidCommandIsProvided() {
        // Given
        int permissionId = 1;
        int applicationId = 100;
        String permissionName = "manage";
        String appCode = "APP";
        String permissionPreviousName = "OLD_NAME";
        String permissionPreviousDescription = "Old Description";
        String permissionNewName = "NEW_NAME";
        String permissionNewDescription = "New Description";

        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);
        application.setCode(appCode);

        PermissionEntity existingPermission = new PermissionEntity();
        existingPermission.setId(permissionId);

        existingPermission.setName(permissionPreviousName);
        existingPermission.setDescription(permissionPreviousDescription);
        existingPermission.setStatus(Status.INACTIVE);
        existingPermission.setApplication(application);

        PermissionDTO dto = new PermissionDTO();
        dto.setName(permissionNewName);
        dto.setDescription(permissionNewDescription);
        dto.setStatus(Status.ACTIVE);
        dto.setApplicationCode(appCode);

        UpdatePermissionCommand command = new UpdatePermissionCommand(dto, permissionName);

        PermissionEntity updatedPermission = new PermissionEntity();
        PermissionDTO mappedDTO = new PermissionDTO();

        // When
        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED)).thenReturn(Optional.of(existingPermission));
        when(applicationRepository.findByCode(appCode)).thenReturn(Optional.of(application));
        when(permissionRepository.save(any(PermissionEntity.class))).thenReturn(updatedPermission);
        when(permissionMapper.mapToDTO(updatedPermission)).thenReturn(mappedDTO);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity savedPermission = permissionCaptor.getValue();

        assertEquals(permissionNewName, savedPermission.getName());
        assertEquals(permissionNewDescription, savedPermission.getDescription());
        assertEquals(Status.ACTIVE, savedPermission.getStatus());
        assertEquals(appCode, savedPermission.getApplication().getCode());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mappedDTO, response.getBody());
    }
}