package cv.igrp.platform.access_management.security_audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {

    @Mock private SecurityAuditLogRepository repository;
    @Mock private SecurityAuditContextProvider contextProvider;
    @Mock private SecurityAuditChainService chainService;

    private SecurityAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SecurityAuditServiceImpl(repository, contextProvider, chainService, new ObjectMapper());
    }

    private Map<String, Object> baseContext() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("userId", "user-1");
        ctx.put("username", "alice");
        ctx.put("ipAddress", "203.0.113.7");
        ctx.put("userAgent", "JUnit");
        return ctx;
    }

    @Test
    void logEventAppendsThroughTheChainWithMergedContext() {
        when(contextProvider.getContext()).thenReturn(baseContext());

        service.logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of("method", "OIDC"));

        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(chainService).append(captor.capture());
        SecurityAuditLogEntity saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(saved.getCategory()).isEqualTo(AuditCategory.AUTHENTICATION);
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getTimestamp()).isNotNull();
        assertThat(saved.getContextData()).contains("\"method\":\"OIDC\"", "\"userId\":\"user-1\"");
    }

    @Test
    void auditWriteFailureNeverPropagatesToTheCaller() {
        when(contextProvider.getContext()).thenReturn(baseContext());
        doThrow(new RuntimeException("db down")).when(chainService).append(org.mockito.ArgumentMatchers.any());

        assertThatCode(() ->
                service.logEvent(AuditEventType.LOGIN_SUCCESS, AuditCategory.AUTHENTICATION, Map.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void logAccessDeniedRecordsAuthorizationDecisionReason() {
        when(contextProvider.getContext()).thenReturn(baseContext());

        service.logAccessDenied("igrp.audit.view", "no grant");

        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(chainService).append(captor.capture());
        SecurityAuditLogEntity saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo(AuditEventType.ACCESS_DENIED);
        assertThat(saved.getCategory()).isEqualTo(AuditCategory.AUTHORIZATION);
        assertThat(saved.getDecisionReason()).isEqualTo("no grant");
    }
}
