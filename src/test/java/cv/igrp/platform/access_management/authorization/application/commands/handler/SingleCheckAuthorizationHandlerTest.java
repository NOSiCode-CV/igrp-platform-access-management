package cv.igrp.platform.access_management.authorization.application.commands.handler;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SingleCheckAuthorizationHandler}.
 */
@ExtendWith(MockitoExtension.class)
class SingleCheckAuthorizationHandlerTest {

    @Mock
    private AuthorizationCore authorizationCore;

    @InjectMocks
    private SingleCheckAuthorizationHandler handler;

    private PermissionCheckResponse permissionCheckResponse;

    @BeforeEach
    void setUp() {
        permissionCheckResponse = new PermissionCheckResponse();
        permissionCheckResponse.setAllowed(true);
        permissionCheckResponse.setViaRoles(List.of("ADMIN"));
        permissionCheckResponse.setReason("Access granted");
        permissionCheckResponse.setCacheHit(true);
        permissionCheckResponse.setResolutionTimeMs(12);
    }

    @Test
    void testCheckAuthorization_ShouldReturnValidResponse() {
        when(authorizationCore.check(any(PermissionCheckRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(permissionCheckResponse));

        PermissionCheckResponseDTO dto = handler.checkAuthorization("john", "read", "document");

        assertNotNull(dto);
        assertTrue(dto.isAllowed());
        assertEquals(List.of("ADMIN"), dto.getViaRoles());
        assertEquals("Access granted", dto.getReason());
        assertTrue(dto.isCacheHit());
        assertEquals(12, dto.getResolutionTimeMs());
    }

    @Test
    void testCheckAuthorization_WhenFutureThrowsException_ShouldWrapAndThrowRuntimeException() {
        // Mock the async call to throw an ExecutionException when .get() is called
        CompletableFuture<PermissionCheckResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Backend failure"));
        when(authorizationCore.check(any(PermissionCheckRequest.class))).thenReturn(failedFuture);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                handler.checkAuthorization("john", "read", "document")
        );

        assertInstanceOf(ExecutionException.class, ex.getCause());
        assertInstanceOf(RuntimeException.class, ex.getCause().getCause());
        assertEquals("Backend failure", ex.getCause().getCause().getMessage());
    }
}