package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.application.dto.AccessReportRowDTO;
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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class GetAccessReportQueryHandlerTest {

    @Mock private SecurityAuditLogRepository repository;
    private GetAccessReportQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetAccessReportQueryHandler(repository);
    }

    @Test
    void mapsEntityToAccessReportRow() {
        LocalDateTime ts = LocalDateTime.of(2025, 6, 1, 8, 14, 0);
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setTimestamp(ts);
        e.setUsername("joao.silva");
        e.setAccessRole("Customer");
        e.setApplicationModule("Customer Management");
        e.setAction("New Account Creation");
        e.setIpAddress("10.0.1.45");
        e.setStatus(AuditStatus.SUCCESS);

        doReturn(new PageImpl<>(List.of(e)))
                .when(repository).findAll(any(Specification.class), any(PageRequest.class));

        var query = new GetAccessReportQuery(
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 6, 30, 0, 0),
                null, null, null, null, null, PageRequest.of(0, 20));

        ResponseEntity<Page<AccessReportRowDTO>> response = handler.handle(query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        AccessReportRowDTO dto = response.getBody().getContent().get(0);
        assertThat(dto.getTimestamp()).isEqualTo(ts.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(dto.getUsername()).isEqualTo("joao.silva");
        assertThat(dto.getRole()).isEqualTo("Customer");
        assertThat(dto.getModule()).isEqualTo("Customer Management");
        assertThat(dto.getAction()).isEqualTo("New Account Creation");
        assertThat(dto.getIpAddress()).isEqualTo("10.0.1.45");
        assertThat(dto.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }
}
