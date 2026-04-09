package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class GetSecurityAuditLogsQueryHandler implements QueryHandler<GetSecurityAuditLogsQuery, ResponseEntity<Page<SecurityAuditLogDTO>>> {

    private final SecurityAuditLogRepository repository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public GetSecurityAuditLogsQueryHandler(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<Page<SecurityAuditLogDTO>> handle(GetSecurityAuditLogsQuery query) {
        Page<SecurityAuditLogDTO> logs = repository.findAll(query.getPageable())
                .map(this::toDTO);
        return ResponseEntity.ok(logs);
    }

    private SecurityAuditLogDTO toDTO(SecurityAuditLogEntity entity) {
        SecurityAuditLogDTO dto = new SecurityAuditLogDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUsername(entity.getUsername());
        dto.setSessionId(entity.getSessionId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        // correlationId, requestPath, decisionReason are missing in entity, setting to null
        dto.setEventType(entity.getEventType() != null ? entity.getEventType().name() : null);
        dto.setCategory(entity.getCategory() != null ? entity.getCategory().name() : null);
        dto.setContextData(entity.getContextData());
        dto.setTimestamp(entity.getTimestamp() != null ? entity.getTimestamp().format(FORMATTER) : null);
        return dto;
    }
}
