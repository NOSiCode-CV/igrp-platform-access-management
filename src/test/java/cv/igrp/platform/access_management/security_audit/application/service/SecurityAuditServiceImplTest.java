package cv.igrp.platform.access_management.security_audit.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
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

    @Test
    void logSettingsEventPopulatesTypedColumnsAndDefaultsStatusToSuccess() {
        when(contextProvider.getContext()).thenReturn(baseContext());

        service.logSettingsEvent(
                SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.ASSOCIATE,
                "TEST.Administrator", "APPROVE_TRANSFERS", null, null);

        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(chainService).append(captor.capture());
        SecurityAuditLogEntity saved = captor.getValue();

        // A settings event is recorded as a SYSTEM configuration change.
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.SYSTEM_CONFIGURATION_CHANGED);
        assertThat(saved.getCategory()).isEqualTo(AuditCategory.SYSTEM);
        // Ambient request context is still captured.
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getIpAddress()).isEqualTo("203.0.113.7");
        // Typed report columns.
        assertThat(saved.getSettingsArea()).isEqualTo(SettingsArea.ACCESS);
        assertThat(saved.getSettingsEntityType()).isEqualTo(SettingsEntityType.ROLE);
        assertThat(saved.getSettingsOperation()).isEqualTo(SettingsOperation.ASSOCIATE);
        assertThat(saved.getEntityName()).isEqualTo("TEST.Administrator");
        assertThat(saved.getRelatedEntity()).isEqualTo("APPROVE_TRANSFERS");
        assertThat(saved.getPreviousValue()).isNull();
        assertThat(saved.getNewValue()).isNull();
        // Status defaults to SUCCESS on the 7-arg overload.
        assertThat(saved.getStatus()).isEqualTo(AuditStatus.SUCCESS);
        // N7: settings rows use the typed columns, not the deprecated JSON blob.
        assertThat(saved.getContextData()).isNull();
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void logSettingsEventCapturesEditValuesAndExplicitStatus() {
        when(contextProvider.getContext()).thenReturn(baseContext());

        service.logSettingsEvent(
                SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.EDIT,
                "Transfers", null, "name=Old→New", "status=ACTIVE→INACTIVE", AuditStatus.ERROR);

        ArgumentCaptor<SecurityAuditLogEntity> captor = ArgumentCaptor.forClass(SecurityAuditLogEntity.class);
        verify(chainService).append(captor.capture());
        SecurityAuditLogEntity saved = captor.getValue();

        assertThat(saved.getSettingsOperation()).isEqualTo(SettingsOperation.EDIT);
        assertThat(saved.getPreviousValue()).isEqualTo("name=Old→New");
        assertThat(saved.getNewValue()).isEqualTo("status=ACTIVE→INACTIVE");
        assertThat(saved.getStatus()).isEqualTo(AuditStatus.ERROR);
    }

    @Test
    void logSettingsEventWriteFailureNeverPropagatesToTheCaller() {
        when(contextProvider.getContext()).thenReturn(baseContext());
        doThrow(new RuntimeException("db down")).when(chainService).append(org.mockito.ArgumentMatchers.any());

        assertThatCode(() -> service.logSettingsEvent(
                SettingsArea.ACCESS, SettingsEntityType.DEPARTMENT, SettingsOperation.CREATE,
                "Finance", null, null, null))
                .doesNotThrowAnyException();
    }
}
