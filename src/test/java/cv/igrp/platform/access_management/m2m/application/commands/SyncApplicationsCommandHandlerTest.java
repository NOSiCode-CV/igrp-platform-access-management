package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.platform.access_management.m2m.domain.service.ApplicationSyncService;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SyncApplicationsCommandHandler.
 */
@ExtendWith(MockitoExtension.class)
class SyncApplicationsCommandHandlerTest {

    @Mock
    private ApplicationSyncService applicationSyncService;

    @InjectMocks
    private SyncApplicationsCommandHandler syncApplicationsCommandHandler;

    private ApplicationDTO dto;
    private SyncApplicationsCommand command;

    @BeforeEach
    void setUp() {
        dto = new ApplicationDTO();
        dto.setCode("APP001");
        dto.setName("Application One");
        dto.setDescription("Test Application");
        dto.setStatus(Status.ACTIVE);
        dto.setType(AppType.INTERNAL);

        command = new SyncApplicationsCommand(dto);
    }

    /**
     * Case 1: New application DTO arrives — Application created successfully.
     */
    @Test
    void testHandle_NewApplication_ShouldReturnNoContent() {
        doNothing().when(applicationSyncService).synchronizeApplication(dto);

        ResponseEntity<String> response = syncApplicationsCommandHandler.handle(command);

        verify(applicationSyncService, times(1)).synchronizeApplication(dto);
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 2: Same DTO arrives again — No changes applied.
     */
    @Test
    void testHandle_SameApplication_ShouldReturnNoContent() {
        // Simulate no change in sync (still success, no exception)
        doNothing().when(applicationSyncService).synchronizeApplication(dto);

        ResponseEntity<String> response = syncApplicationsCommandHandler.handle(command);

        verify(applicationSyncService, times(1)).synchronizeApplication(dto);
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    /**
     * Case 3: Validation error — Missing code or invalid data.
     */
    @Test
    void testHandle_ValidationError_ShouldReturnBadRequest() {
        ApplicationDTO invalidDto = new ApplicationDTO(); // missing code
        SyncApplicationsCommand invalidCommand = new SyncApplicationsCommand(invalidDto);

        doThrow(IgrpResponseStatusException.badRequest("Application code is required"))
                .when(applicationSyncService).synchronizeApplication(invalidDto);

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncApplicationsCommandHandler.handle(invalidCommand)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Application code is required"));
    }

    /**
     * Case 4: Internal server error — unexpected runtime error.
     */
    @Test
    void testHandle_InternalError_ShouldReturnServerError() {
        doThrow(IgrpResponseStatusException.internalServerError("Database failure"))
                .when(applicationSyncService).synchronizeApplication(dto);

        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> syncApplicationsCommandHandler.handle(command)
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Database failure"));
    }
}
