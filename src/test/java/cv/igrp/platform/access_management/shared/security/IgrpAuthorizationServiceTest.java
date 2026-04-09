package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.shared.security.policy.Policy;
import cv.igrp.platform.access_management.shared.security.policy.PolicyDecision;
import cv.igrp.platform.access_management.shared.security.policy.ResourceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IgrpAuthorizationServiceTest {

    private IgrpAuthorizationService authorizationService;

    @Mock
    private SingleCheckAuthorizationHandler authorizationHandler;

    @Mock
    private AuthenticationHelper authHelper;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private Policy policy;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authorizationService = new IgrpAuthorizationService(
                authorizationHandler,
                authHelper,
                List.of(policy),
                auditService
        );

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authHelper.getSub()).thenReturn("testuser");
    }

    @Test
    void allow_shouldReturnTrue_whenPermissionAndPolicyAllow() {
        // Arrange
        String action = "users.view";
        var response = new PermissionCheckResponseDTO();
        response.setAllowed(true);
        when(authorizationHandler.checkAuthorization(anyString(), eq(action), any())).thenReturn(response);
        when(policy.evaluate(any(), eq(action), any())).thenReturn(PolicyDecision.allow());

        // Act
        boolean result = authorizationService.allow(action, "resource-1", Map.of("ownerId", "testuser"));

        // Assert
        assertTrue(result);
        verify(authorizationHandler).checkAuthorization("testuser", action, "resource-1");
        verify(policy).evaluate(eq(authentication), eq(action), any(ResourceContext.class));
    }

    @Test
    void allow_shouldReturnFalse_whenPermissionDenies() {
        // Arrange
        String action = "users.view";
        var response = new PermissionCheckResponseDTO();
        response.setAllowed(false);
        when(authorizationHandler.checkAuthorization(anyString(), eq(action), any())).thenReturn(response);

        // Act
        boolean result = authorizationService.allow(action, null, null);

        // Assert
        assertFalse(result);
        verify(auditService).logAccessDenied(eq(action), contains("Permission gate denied"));
        verifyNoInteractions(policy);
    }

    @Test
    void allow_shouldReturnFalse_whenPolicyDenies() {
        // Arrange
        String action = "users.view";
        var response = new PermissionCheckResponseDTO();
        response.setAllowed(true);
        when(authorizationHandler.checkAuthorization(anyString(), eq(action), any())).thenReturn(response);
        when(policy.evaluate(any(), eq(action), any())).thenReturn(PolicyDecision.deny("Not the owner"));

        // Act
        boolean result = authorizationService.allow(action, "resource-1", Map.of("ownerId", "otheruser"));

        // Assert
        assertFalse(result);
        verify(auditService).logEvent(any(), any(), anyMap());
    }
}
