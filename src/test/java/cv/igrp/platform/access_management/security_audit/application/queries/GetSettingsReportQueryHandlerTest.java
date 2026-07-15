package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.platform.access_management.security_audit.application.dto.SettingsReportRowDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
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
class GetSettingsReportQueryHandlerTest {

    @Mock private SecurityAuditLogRepository repository;
    private GetSettingsReportQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetSettingsReportQueryHandler(repository);
    }

    @Test
    void mapsEntityToSettingsReportRow() {
        LocalDateTime ts = LocalDateTime.of(2025, 6, 2, 9, 3, 0);
        SecurityAuditLogEntity e = new SecurityAuditLogEntity();
        e.setTimestamp(ts);
        e.setUsername("superadmin@igrp.cv");
        e.setSettingsArea(SettingsArea.ACCESS);
        e.setSettingsEntityType(SettingsEntityType.ROLE);
        e.setSettingsOperation(SettingsOperation.ASSOCIATE);
        e.setEntityName("TEST.Administrator");
        e.setRelatedEntity("APPROVE_TRANSFERS");
        e.setIpAddress("10.0.2.88");
        e.setStatus(AuditStatus.SUCCESS);

        doReturn(new PageImpl<>(List.of(e)))
                .when(repository).findAll(any(Specification.class), any(PageRequest.class));

        var query = new GetSettingsReportQuery(
                LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 6, 30, 0, 0),
                null, null, null, null, null, PageRequest.of(0, 20));

        ResponseEntity<Page<SettingsReportRowDTO>> response = handler.handle(query);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SettingsReportRowDTO dto = response.getBody().getContent().get(0);
        assertThat(dto.getTimestamp()).isEqualTo(ts.atZone(ZoneId.systemDefault()).toInstant());
        assertThat(dto.getPerformedBy()).isEqualTo("superadmin@igrp.cv");
        assertThat(dto.getArea()).isEqualTo(SettingsArea.ACCESS);
        assertThat(dto.getEntityType()).isEqualTo(SettingsEntityType.ROLE);
        assertThat(dto.getOperation()).isEqualTo(SettingsOperation.ASSOCIATE);
        assertThat(dto.getEntityName()).isEqualTo("TEST.Administrator");
        assertThat(dto.getRelatedEntity()).isEqualTo("APPROVE_TRANSFERS");
        assertThat(dto.getPreviousValue()).isNull();
        assertThat(dto.getNewValue()).isNull();
        assertThat(dto.getIpAddress()).isEqualTo("10.0.2.88");
        assertThat(dto.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }
}
