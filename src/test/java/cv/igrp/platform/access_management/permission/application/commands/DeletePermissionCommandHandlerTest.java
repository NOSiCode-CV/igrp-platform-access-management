package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeletePermissionCommandHandlerTest {

    @InjectMocks
    private DeletePermissionCommandHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_WhenProvided_Id_NotFound() {
        //... Given
        String permissionName = "manage";
        DeletePermissionCommand command = new DeletePermissionCommand(permissionName);

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldSetStatusToDeleted_WhenPermissionExists() {
        //... Given
        int permissionId = 100;
        String permissionName = "manage";
        DeletePermissionCommand command = new DeletePermissionCommand(permissionName);
        PermissionEntity foundPermission = new PermissionEntity();
        foundPermission.setId(permissionId);
        foundPermission.setStatus(Status.ACTIVE);
        String permissionDesc = "Permission Name";
        foundPermission.setDescription(permissionDesc);

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
                .thenReturn(Optional.of(foundPermission));

        //... When
        underTest.handle(command);

        //... Then
        verify(permissionRepository).findByNameAndStatusNot(permissionName, Status.DELETED);
        assertEquals(Status.DELETED, foundPermission.getStatus());
    }

    @Test
    void itShouldReturnNoContent_WhenDeletionIsSuccessful() {
        //... Given
        int permissionId = 100;
        String permissionName = "manage";
        DeletePermissionCommand command = new DeletePermissionCommand(permissionName);
        PermissionEntity foundPermission = new PermissionEntity();
        foundPermission.setId(permissionId);
        foundPermission.setStatus(Status.ACTIVE);
        String permissionDesc = "Permission Name";
        foundPermission.setDescription(permissionDesc);

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
                .thenReturn(Optional.of(foundPermission));

        //... When
        ResponseEntity<Boolean> response = underTest.handle(command);

        //... Then
        verify(permissionRepository).findByNameAndStatusNot(permissionName, Status.DELETED);
        assertEquals(Status.DELETED, foundPermission.getStatus());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void itShouldSaveUpdatedPermissionToRepository() {
        //... Given
        int permissionId = 100;
        String permissionName = "manage";
        DeletePermissionCommand command = new DeletePermissionCommand(permissionName);
        PermissionEntity foundPermission = new PermissionEntity();
        foundPermission.setId(permissionId);
        foundPermission.setStatus(Status.ACTIVE);
        String permissionDesc = "Permission Name";
        foundPermission.setDescription(permissionDesc);

        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
                .thenReturn(Optional.of(foundPermission));

        //... When
        ResponseEntity<Boolean> response = underTest.handle(command);

        //... Then
        verify(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED));
        verify(permissionRepository).save(foundPermission);
        assertEquals(Status.DELETED, foundPermission.getStatus());
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}