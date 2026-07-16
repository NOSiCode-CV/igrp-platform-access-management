package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.application.dto.AuditReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class GetAuditReportQueryHandlerTest {

    @Mock private SecurityAuditLogRepository repository;
    private GetAuditReportQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetAuditReportQueryHandler(repository);
    }

    @Test
    void mapsEntityToAuditReportRowAndDerivesDevice() {
        UUID id = UUID.randomUUID();
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setId(id);
        e.setPeriodStart(Instant.parse("2025-06-01T08:14:00Z"));
        e.setPeriodEnd(Instant.parse("2025-06-08T09:12:00Z"));
        e.setUsername("joao.silva");
        e.setApplicationModule("Transfers");
        e.setAccessRole("DGT Technician");
        e.setOperationState("Completed");
        e.setIpAddress("10.0.1.45");
        e.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        e.setAuthorizedBy("ana.ferreira");
        e.setStatus(AuditStatus.SUCCESS);

        doReturn(new PageImpl<>(List.of(e)))
                .when(repository).findAll(any(Specification.class), any(PageRequest.class));

        var query = new GetAuditReportQuery(
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 6, 30, 0, 0),
                null, null, null, null, null, null, PageRequest.of(0, 20));

        ResponseEntity<Page<AuditReportRowDTO>> response = handler.handle(query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AuditReportRowDTO dto = response.getBody().getContent().get(0);
        assertThat(dto.getId()).isEqualTo(id.toString());
        assertThat(dto.getStartDate()).isEqualTo(Instant.parse("2025-06-01T08:14:00Z"));
        assertThat(dto.getEndDate()).isEqualTo(Instant.parse("2025-06-08T09:12:00Z"));
        assertThat(dto.getUsername()).isEqualTo("joao.silva");
        assertThat(dto.getModule()).isEqualTo("Transfers");
        assertThat(dto.getAccessRole()).isEqualTo("DGT Technician");
        assertThat(dto.getOperationState()).isEqualTo("Completed");
        assertThat(dto.getIpAddress()).isEqualTo("10.0.1.45");
        assertThat(dto.getDevice()).isEqualTo("Chrome/Windows");
        assertThat(dto.getAuthorizedBy()).isEqualTo("ana.ferreira");
        assertThat(dto.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }
}
