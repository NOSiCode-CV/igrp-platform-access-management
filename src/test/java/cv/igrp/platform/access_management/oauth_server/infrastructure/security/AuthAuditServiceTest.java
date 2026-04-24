package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.application.dto.AuthAuditDTO;
import cv.igrp.platform.access_management.oauth_server.domain.models.AuthEventType;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.AuthAuditLogEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.AuthAuditLogJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAuditServiceTest {

    @Mock private AuthAuditLogJpaRepository repository;

    private AuthAuditService service;

    @BeforeEach
    void setUp() {
        service = new AuthAuditService(repository);
    }

    @Test
    void logPersistsWithIdEventTypeAndUsername() {
        service.log("demo", AuthEventType.LOGIN_SUCCESS);

        ArgumentCaptor<AuthAuditLogEntity> captor = ArgumentCaptor.forClass(AuthAuditLogEntity.class);
        verify(repository).save(captor.capture());
        AuthAuditLogEntity saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals("demo", saved.getUsername());
        assertEquals(AuthEventType.LOGIN_SUCCESS, saved.getEventType());
        assertNotNull(saved.getTimestamp());
    }

    @Test
    void getLogsByUserHitsUsernameFilter() {
        AuthAuditLogEntity entry = sampleEntry("demo", AuthEventType.LOGIN_FAILURE);
        when(repository.findByUsernameOrderByTimestampDesc("demo")).thenReturn(List.of(entry));

        List<AuthAuditDTO> out = service.getLogsByUser("demo");
        assertEquals(1, out.size());
        assertEquals("demo", out.get(0).getUsername());
        assertEquals(AuthEventType.LOGIN_FAILURE, out.get(0).getEventType());
    }

    @Test
    void getAllLogsReturnsTransformedEntries() {
        AuthAuditLogEntity e1 = sampleEntry("a", AuthEventType.LOGIN_SUCCESS);
        AuthAuditLogEntity e2 = sampleEntry("b", AuthEventType.LOGOUT);
        when(repository.findAllByOrderByTimestampDesc()).thenReturn(List.of(e1, e2));
        assertEquals(2, service.getAllLogs().size());
    }

    private AuthAuditLogEntity sampleEntry(String username, AuthEventType type) {
        AuthAuditLogEntity e = new AuthAuditLogEntity();
        e.setId(UUID.randomUUID());
        e.setUsername(username);
        e.setEventType(type);
        e.setTimestamp(LocalDateTime.now());
        return e;
    }
}
