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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link CheckAuthorizationCommandHandler}.
 */
@ExtendWith(MockitoExtension.class)
class CheckAuthorizationCommandHandlerTest {

    @Mock
    private AuthenticationHelper authenticationHelper;

    @Mock
    private SingleCheckAuthorizationHandler singleCheckAuthorizationHandler;

    @InjectMocks
    private CheckAuthorizationCommandHandler handler;

    private CheckAuthorizationCommand command;

    @BeforeEach
    void setUp() {
        var request = new PermissionCheckRequestDTO();
        request.setAction("read");
        request.setResource("document");
        command = new CheckAuthorizationCommand(request);
    }

    @Test
    void testHandle_ShouldReturnOkResponseWithExpectedBody() {
        String sub = "f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454";
        PermissionCheckResponseDTO expectedResponse = new PermissionCheckResponseDTO();
        expectedResponse.setAllowed(true);
        expectedResponse.setViaRoles(List.of("ADMIN"));

        when(authenticationHelper.getPreferredUsername()).thenReturn(sub);
        when(singleCheckAuthorizationHandler.checkAuthorization(sub, "read", "document"))
                .thenReturn(expectedResponse);

        ResponseEntity<PermissionCheckResponseDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedResponse, response.getBody());

        verify(authenticationHelper).getPreferredUsername();
        verify(singleCheckAuthorizationHandler).checkAuthorization(sub, "read", "document");
    }

    @Test
    void testHandle_WhenHandlerReturnsNull_ShouldStillReturnOkResponse() {
        String sub = "a1b5fc1e-2fab-8e6a-e4d3-10a59b3c1029";
        when(authenticationHelper.getPreferredUsername()).thenReturn(sub);
        when(singleCheckAuthorizationHandler.checkAuthorization(sub, "read", "document"))
                .thenReturn(null);

        ResponseEntity<PermissionCheckResponseDTO> response = handler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());
    }
}
