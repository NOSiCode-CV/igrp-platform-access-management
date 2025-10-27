package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.platform.access_management.m2m.domain.service.PermissionSyncService;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SyncPermissionsCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class SyncPermissionsCommandHandlerTest {

    @Mock
    private PermissionSyncService permissionSyncService;

    @InjectMocks
    private SyncPermissionsCommandHandler syncPermissionsCommandHandler;

    private SyncPermissionsCommand command;

    @BeforeEach
    void setUp() {
        PermissionDTO permissionDTO = new PermissionDTO();
        permissionDTO.setName("PERM_VIEW");
        permissionDTO.setDescription("Allows viewing permission entities");

        command = new SyncPermissionsCommand(List.of(permissionDTO));
    }

    /**
     * Case 1: New permission DTO arrives — Permission created successfully.
     */
    @Test
    void testHandle_NewPermission_ShouldReturnNoContent() {
        doNothing().when(permissionSyncService).synchronizePermissions(command.getPermissiondto());

        ResponseEntity<String> response = syncPermissionsCommandHandler.handle(command);

        verify(permissionSyncService, times(1)).synchronizePermissions(command.getPermissiondto());
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 2: Same DTO arrives again — No changes applied (hash match).
     */
    @Test
    void testHandle_SamePermission_ShouldReturnNoContent() {
        // Simulate no change detected in sync process
        doNothing().when(permissionSyncService).synchronizePermissions(command.getPermissiondto());

        ResponseEntity<String> response = syncPermissionsCommandHandler.handle(command);

        verify(permissionSyncService, times(1)).synchronizePermissions(command.getPermissiondto());
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 3: Validation error — Missing required fields or invalid data.
     */
    @Test
    void testHandle_ValidationError_ShouldThrowBadRequest() {
        PermissionDTO invalidDto = new PermissionDTO(); // Missing code
        SyncPermissionsCommand invalidCommand = new SyncPermissionsCommand(List.of(invalidDto));

        doThrow(IgrpResponseStatusException.badRequest("Permission code is required"))
                .when(permissionSyncService).synchronizePermissions(invalidCommand.getPermissiondto());

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncPermissionsCommandHandler.handle(invalidCommand)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Permission code is required"));
    }

    /**
     * Case 4: Internal server error — unexpected runtime error.
     */
    @Test
    void testHandle_InternalError_ShouldThrowInternalServerError() {
        doThrow(IgrpResponseStatusException.internalServerError("Database failure"))
                .when(permissionSyncService).synchronizePermissions(command.getPermissiondto());

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncPermissionsCommandHandler.handle(command)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Database failure"));
    }
}
