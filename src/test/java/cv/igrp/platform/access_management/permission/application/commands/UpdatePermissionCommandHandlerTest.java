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
        String permissionNewName = "Permission New Name";
        String permissionNewDescription = "PermissionNewId";

        PermissionDTO permissionNewData = new PermissionDTO();

        permissionNewData.setName(permissionNewName);
        permissionNewData.setDescription(permissionNewDescription);
        permissionNewData.setStatus(Status.INACTIVE);
        UpdatePermissionCommand command = new UpdatePermissionCommand(permissionNewData, permissionId);

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
        int applicationId = 100;
        String permissionNewName = "Permission New Name";
        String permissionNewDescription = "PermissionNewId";
        String permissionPreviousName = "Permission PreviousName";

        PermissionDTO permissionNewData = new PermissionDTO();

        permissionNewData.setName(permissionNewName);
        permissionNewData.setDescription(permissionNewDescription);
        permissionNewData.setStatus(Status.INACTIVE);
        permissionNewData.setApplicationId(applicationId);
        UpdatePermissionCommand command = new UpdatePermissionCommand(permissionNewData, permissionId);

        PermissionEntity foundPermission = new PermissionEntity();
        foundPermission.setId(permissionId);
        foundPermission.setName(permissionPreviousName);

        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED))
                .thenReturn(Optional.of(foundPermission));
        when(applicationRepository.findById(applicationId))
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
        int previousAppId = 10;
        int newAppId = 20;

        ApplicationEntity previousApp = new ApplicationEntity();
        previousApp.setId(previousAppId);

        ApplicationEntity newApp = new ApplicationEntity();
        newApp.setId(newAppId);

        PermissionEntity existingPermission = new PermissionEntity();
        existingPermission.setId(permissionId);
        existingPermission.setName("READ_DATA");
        existingPermission.setStatus(Status.ACTIVE);
        existingPermission.setApplication(previousApp);

        PermissionDTO updateDTO = new PermissionDTO();
        updateDTO.setApplicationId(newAppId);
        updateDTO.setName("READ_DATA");
        updateDTO.setDescription("Updated Description");
        updateDTO.setStatus(Status.ACTIVE);

        UpdatePermissionCommand command = new UpdatePermissionCommand(updateDTO, permissionId);

        PermissionEntity savedPermission = new PermissionEntity();
        PermissionDTO mappedDTO = new PermissionDTO();

        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED)).thenReturn(Optional.of(existingPermission));
        when(applicationRepository.findById(newAppId)).thenReturn(Optional.of(newApp));
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
        String permissionPreviousName = "OLD_NAME";
        String permissionPreviousDescription = "Old Description";
        String permissionNewName = "NEW_NAME";
        String permissionNewDescription = "New Description";

        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);

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
        dto.setApplicationId(applicationId);

        UpdatePermissionCommand command = new UpdatePermissionCommand(dto, permissionId);

        PermissionEntity updatedPermission = new PermissionEntity();
        PermissionDTO mappedDTO = new PermissionDTO();

        // When
        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED)).thenReturn(Optional.of(existingPermission));
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(permissionRepository.save(any(PermissionEntity.class))).thenReturn(updatedPermission);
        when(permissionMapper.mapToDTO(updatedPermission)).thenReturn(mappedDTO);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity savedPermission = permissionCaptor.getValue();

        assertEquals(permissionNewName, savedPermission.getName());
        assertEquals(permissionNewDescription, savedPermission.getDescription());
        assertEquals(Status.ACTIVE, savedPermission.getStatus());
        assertEquals(applicationId, savedPermission.getApplication().getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mappedDTO, response.getBody());
    }
}