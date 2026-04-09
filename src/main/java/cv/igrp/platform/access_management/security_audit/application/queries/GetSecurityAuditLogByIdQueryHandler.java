package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class GetSecurityAuditLogByIdQueryHandler implements QueryHandler<GetSecurityAuditLogByIdQuery, ResponseEntity<SecurityAuditLogDTO>> {

    private final SecurityAuditLogRepository repository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public GetSecurityAuditLogByIdQueryHandler(SecurityAuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<SecurityAuditLogDTO> handle(GetSecurityAuditLogByIdQuery query) {
        return repository.findById(query.getId())
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private SecurityAuditLogDTO toDTO(SecurityAuditLogEntity entity) {
        SecurityAuditLogDTO dto = new SecurityAuditLogDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUsername(entity.getUsername());
        dto.setSessionId(entity.getSessionId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        dto.setEventType(entity.getEventType() != null ? entity.getEventType().name() : null);
        dto.setCategory(entity.getCategory() != null ? entity.getCategory().name() : null);
        dto.setContextData(entity.getContextData());
        dto.setTimestamp(entity.getTimestamp() != null ? entity.getTimestamp().format(FORMATTER) : null);
        return dto;
    }
}
