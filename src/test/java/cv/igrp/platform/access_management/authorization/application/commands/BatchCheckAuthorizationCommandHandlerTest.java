package cv.igrp.platform.access_management.authorization.application.commands;

import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckRequestDTO;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BatchCheckAuthorizationCommandHandler}.
 */
@ExtendWith(MockitoExtension.class)
class BatchCheckAuthorizationCommandHandlerTest {

    @Mock
    private AuthenticationHelper authenticationHelper;

    @Mock
    private SingleCheckAuthorizationHandler singleCheckAuthorizationHandler;

    @InjectMocks
    private BatchCheckAuthorizationCommandHandler handler;

    private BatchCheckAuthorizationCommand command;
    private PermissionCheckResponseDTO response1;
    private PermissionCheckResponseDTO response2;

    @BeforeEach
    void setUp() {
        // Prepare mock request DTOs
        PermissionCheckRequestDTO dto1 = new PermissionCheckRequestDTO();
        dto1.setAction("read");
        dto1.setResource("document");

        PermissionCheckRequestDTO dto2 = new PermissionCheckRequestDTO();
        dto2.setAction("write");
        dto2.setResource("report");

        command = new BatchCheckAuthorizationCommand();
        command.setPermissioncheckrequest(List.of(dto1, dto2));

        // Prepare mock responses
        response1 = new PermissionCheckResponseDTO();
        response1.setAllowed(true);
        response1.setReason("Allowed by admin role");

        response2 = new PermissionCheckResponseDTO();
        response2.setAllowed(false);
        response2.setReason("Insufficient permissions");
    }

    @Test
    void testHandle_ShouldReturnListOfResponses() {
        when(authenticationHelper.getPreferredUsername()).thenReturn("john");
        when(singleCheckAuthorizationHandler.checkAuthorization("john", "read", "document")).thenReturn(response1);
        when(singleCheckAuthorizationHandler.checkAuthorization("john", "write", "report")).thenReturn(response2);

        ResponseEntity<List<PermissionCheckResponseDTO>> result = handler.handle(command);

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertEquals(2, result.getBody().size());
        assertEquals(response1, result.getBody().get(0));
        assertEquals(response2, result.getBody().get(1));

        verify(authenticationHelper).getPreferredUsername();
        verify(singleCheckAuthorizationHandler, times(2)).checkAuthorization(anyString(), anyString(), anyString());
    }

    @Test
    void testHandle_WithEmptyRequests_ShouldReturnEmptyList() {
        BatchCheckAuthorizationCommand emptyCommand = new BatchCheckAuthorizationCommand();
        emptyCommand.setPermissioncheckrequest(List.of());

        when(authenticationHelper.getPreferredUsername()).thenReturn("john");

        ResponseEntity<List<PermissionCheckResponseDTO>> result = handler.handle(emptyCommand);

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());

        verify(authenticationHelper).getPreferredUsername();
        verifyNoInteractions(singleCheckAuthorizationHandler);
    }
}