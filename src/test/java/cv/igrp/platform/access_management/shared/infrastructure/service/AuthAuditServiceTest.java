package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditLog;
import cv.igrp.platform.access_management.shared.domain.audit.AuthEventType;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AuthAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthAuditServiceTest {

    @Mock
    private AuthAuditLogRepository repository;

    @InjectMocks
    private AuthAuditService service;

    @Test
    void email_password_login_produces_email_identifier_type() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("BasicAuthenticator"));
        when(jwt.hasClaim("acr")).thenReturn(true);
        when(jwt.getClaimAsString("acr")).thenReturn("pwd");
        when(jwt.hasClaim("email")).thenReturn(true);
        when(jwt.getClaimAsString("email")).thenReturn("user@example.com");
        when(jwt.getSubject()).thenReturn("user@example.com");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.EMAIL, context.identifierType());
        assertEquals("user@example.com", context.identifierValue());
        assertEquals("user@example.com", context.userId());
    }

    @Test
    void cmdcv_login_produces_cmdcv_identifier_type() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("OpenIDConnectAuthenticator"));
        when(jwt.hasClaim("acr")).thenReturn(true);
        when(jwt.getClaimAsString("acr")).thenReturn("cmdcv");
        when(jwt.hasClaim("phone_number")).thenReturn(true);
        when(jwt.getClaimAsString("phone_number")).thenReturn("+2391234567");
        when(jwt.getSubject()).thenReturn("12345678A001B");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.CMDCV, context.identifierType());
        assertEquals("+2391234567", context.identifierValue());
        assertEquals("12345678A001B", context.userId());
    }

    @Test
    void cni_login_hashes_sub_as_identifier_value() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("OpenIDConnectAuthenticator"));
        when(jwt.hasClaim("acr")).thenReturn(true);
        when(jwt.getClaimAsString("acr")).thenReturn("cni");
        when(jwt.getSubject()).thenReturn("12345678A001B");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.CNI, context.identifierType());
        assertEquals("12345678A001B", context.identifierValue());
        assertEquals("12345678A001B", context.userId());
    }

    @Test
    void refresh_token_with_phone_number_falls_back_to_cmdcv() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("refresh_token"));
        when(jwt.hasClaim("acr")).thenReturn(false);
        when(jwt.hasClaim("phone_number")).thenReturn(true);
        when(jwt.getClaimAsString("phone_number")).thenReturn("+2391234567");
        when(jwt.getSubject()).thenReturn("12345678A001B");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.CMDCV, context.identifierType());
        assertEquals("+2391234567", context.identifierValue());
    }

    @Test
    void refresh_token_with_email_only_falls_back_to_email() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("refresh_token"));
        when(jwt.hasClaim("acr")).thenReturn(false);
        when(jwt.hasClaim("phone_number")).thenReturn(false);
        when(jwt.hasClaim("email")).thenReturn(true);
        when(jwt.getClaimAsString("email")).thenReturn("user@example.com");
        when(jwt.getSubject()).thenReturn("user@example.com");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.EMAIL, context.identifierType());
        assertEquals("user@example.com", context.identifierValue());
    }

    @Test
    void unknown_acr_produces_unknown_identifier_type() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.hasClaim("amr")).thenReturn(true);
        when(jwt.getClaimAsStringList("amr")).thenReturn(List.of("OpenIDConnectAuthenticator"));
        when(jwt.hasClaim("acr")).thenReturn(true);
        when(jwt.getClaimAsString("acr")).thenReturn("unknown_value");

        AuthAuditContext context = AuthAuditService.fromAutentikaJwt(jwt, null);

        assertEquals(IdentifierType.UNKNOWN, context.identifierType());
        assertNull(context.identifierValue());
    }

    @Test
    void hash_of_null_returns_null() {
        assertNull(AuthAuditService.hash(null));
    }

    @Test
    void hash_of_blank_string_returns_null() {
        assertNull(AuthAuditService.hash("   "));
    }

    @Test
    void hash_of_value_returns_64_char_lowercase_hex() {
        String result = AuthAuditService.hash("user@example.com");
        assertNotNull(result);
        assertEquals(64, result.length());
        assertTrue(result.matches("[0-9a-f]{64}"));
        
        String result2 = AuthAuditService.hash("user@example.com");
        assertEquals(result, result2);
    }

    @Test
    void log_success_saves_row_with_login_success_event_type() {
        AuthAuditContext context = new AuthAuditContext(
                IdentifierType.EMAIL,
                "user@example.com",
                "user@example.com",
                null,
                "session-abc",
                null
        );

        service.logSuccess(context);

        ArgumentCaptor<AuthAuditLog> captor = ArgumentCaptor.forClass(AuthAuditLog.class);
        verify(repository).save(captor.capture());

        AuthAuditLog saved = captor.getValue();
        assertEquals(AuthEventType.LOGIN_SUCCESS, saved.getEventType());
    }

    @Test
    void log_failure_saves_row_with_failure_reason() {
        AuthAuditContext context = new AuthAuditContext(
                IdentifierType.EMAIL,
                "user@example.com",
                "user@example.com",
                null,
                "session-abc",
                null
        );

        service.logFailure(context, "BadCredentialsException");

        ArgumentCaptor<AuthAuditLog> captor = ArgumentCaptor.forClass(AuthAuditLog.class);
        verify(repository).save(captor.capture());

        AuthAuditLog saved = captor.getValue();
        assertEquals(AuthEventType.LOGIN_FAILURE, saved.getEventType());
        assertEquals("BadCredentialsException", saved.getFailureReason());
    }

    @Test
    void log_success_does_not_throw_when_repository_throws() {
        AuthAuditContext context = new AuthAuditContext(
                IdentifierType.EMAIL,
                "user@example.com",
                "user@example.com",
                null,
                "session-abc",
                null
        );

        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertDoesNotThrow(() -> service.logSuccess(context));
    }
}
