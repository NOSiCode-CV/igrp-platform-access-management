package cv.igrp.platform.access_management.shared.infrastructure.security;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthAuditEventListenerTest {

    @Mock
    private AuthAuditService authAuditService;

    @InjectMocks
    private AuthAuditEventListener listener;

    @Test
    void jwt_authentication_triggers_log_success() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("user@example.com");
        when(jwt.getId()).thenReturn("session-abc");
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("BasicAuthenticator"));
        when(jwt.hasClaim("acr")).thenReturn(true);
        when(jwt.getClaimAsString("acr")).thenReturn("pwd");
        when(jwt.hasClaim("email")).thenReturn(true);
        when(jwt.getClaimAsString("email")).thenReturn("user@example.com");

        JwtAuthenticationToken jwtAuth = new JwtAuthenticationToken(jwt, Collections.emptyList());

        listener.onApplicationEvent(new AuthenticationSuccessEvent(jwtAuth));

        verify(authAuditService, times(1)).logSuccess(any(AuthAuditContext.class));
    }

    @Test
    void non_jwt_authentication_is_ignored() {
        UsernamePasswordAuthenticationToken usernamePasswordAuth = 
                new UsernamePasswordAuthenticationToken("user", "pass");

        listener.onApplicationEvent(new AuthenticationSuccessEvent(usernamePasswordAuth));

        verify(authAuditService, never()).logSuccess(any());
    }
}
