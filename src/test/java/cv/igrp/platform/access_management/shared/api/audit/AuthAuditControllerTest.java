package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.framework.core.domain.QueryBus;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.application.queries.GetSecurityAuditLogsQuery;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthAuditControllerTest {

    @Mock private QueryBus queryBus;
    @Mock private SecurityAuditChainValidator chainValidator;
    @Mock private SecurityAuditChainService chainService;

    private AuthAuditController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthAuditController(queryBus, chainValidator, chainService);
    }

    @Test
    void listPassesAllFiltersIntoTheQuery() {
        ResponseEntity<Page<SecurityAuditLogDTO>> expected =
                ResponseEntity.ok(new PageImpl<>(List.of()));
        doReturn(expected).when(queryBus).handle(any(GetSecurityAuditLogsQuery.class));

        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 7, 15, 0, 0);
        var pageable = PageRequest.of(0, 20);

        ResponseEntity<Page<SecurityAuditLogDTO>> response = controller.list(
                "user-1", "alice", "LOGIN_SUCCESS", "AUTHENTICATION", "203.0.113.7", start, end, pageable);

        assertThat(response).isSameAs(expected);

        ArgumentCaptor<GetSecurityAuditLogsQuery> captor =
                ArgumentCaptor.forClass(GetSecurityAuditLogsQuery.class);
        verify(queryBus).handle(captor.capture());
        GetSecurityAuditLogsQuery q = captor.getValue();
        assertThat(q.getUserId()).isEqualTo("user-1");
        assertThat(q.getUsername()).isEqualTo("alice");
        assertThat(q.getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(q.getCategory()).isEqualTo("AUTHENTICATION");
        assertThat(q.getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(q.getStartDate()).isEqualTo(start);
        assertThat(q.getEndDate()).isEqualTo(end);
        assertThat(q.getPageable()).isEqualTo(pageable);
    }

    @Test
    void validateReturnsChainResultAsBody() {
        when(chainValidator.validate())
                .thenReturn(new SecurityAuditChainValidator.Result(true, 5, null, 0));

        ResponseEntity<Map<String, Object>> response = controller.validate();

        assertThat(response.getBody())
                .containsEntry("valid", true)
                .containsEntry("rows_checked", 5)
                .containsEntry("broken_at", null)
                .containsEntry("unverifiable_legacy_rows", 0);
    }

    @Test
    void validateReportsTheFirstBrokenSequence() {
        when(chainValidator.validate())
                .thenReturn(new SecurityAuditChainValidator.Result(false, 3, 4L, 0));

        ResponseEntity<Map<String, Object>> response = controller.validate();

        assertThat(response.getBody())
                .containsEntry("valid", false)
                .containsEntry("broken_at", 4L);
    }

    /** An upgraded DB reports its unhashed legacy prefix instead of failing (Bug B). */
    @Test
    void validateReportsUnverifiableLegacyRows() {
        when(chainValidator.validate())
                .thenReturn(new SecurityAuditChainValidator.Result(true, 4, null, 4717));

        ResponseEntity<Map<String, Object>> response = controller.validate();

        assertThat(response.getBody())
                .containsEntry("valid", true)
                .containsEntry("rows_checked", 4)
                .containsEntry("unverifiable_legacy_rows", 4717);
    }

    @Test
    void purgeReturnsRemovedCount() {
        when(chainService.purge()).thenReturn(7L);

        ResponseEntity<Map<String, Object>> response = controller.purge();

        assertThat(response.getBody()).containsEntry("purged", 7L);
        verify(chainService).purge();
    }
}
