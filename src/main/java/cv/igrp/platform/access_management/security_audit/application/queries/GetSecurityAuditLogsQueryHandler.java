package cv.igrp.platform.access_management.security_audit.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.security_audit.application.dto.SecurityAuditLogDTO;
import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.security_audit.infrastructure.persistence.SecurityAuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
        Page<SecurityAuditLogDTO> logs = repository.findAll(toSpecification(query), query.getPageable())
                .map(this::toDTO);
        return ResponseEntity.ok(logs);
    }

    private Specification<SecurityAuditLogEntity> toSpecification(GetSecurityAuditLogsQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Never surface the GENESIS anchor (sequence 0) in the audit list.
            predicates.add(cb.or(cb.isNull(root.get("sequenceNumber")),
                    cb.notEqual(root.get("sequenceNumber"), 0L)));

            if (hasText(query.getUserId())) {
                predicates.add(cb.equal(root.get("userId"), query.getUserId()));
            }
            if (hasText(query.getUsername())) {
                predicates.add(cb.like(cb.lower(root.get("username")),
                        "%" + query.getUsername().toLowerCase() + "%"));
            }
            if (hasText(query.getEventType())) {
                parseEnum(AuditEventType.class, query.getEventType())
                        .ifPresent(value -> predicates.add(cb.equal(root.get("eventType"), value)));
            }
            if (hasText(query.getCategory())) {
                parseEnum(AuditCategory.class, query.getCategory())
                        .ifPresent(value -> predicates.add(cb.equal(root.get("category"), value)));
            }
            if (hasText(query.getIpAddress())) {
                predicates.add(cb.equal(root.get("ipAddress"), query.getIpAddress()));
            }
            if (query.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), query.getStartDate()));
            }
            if (query.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), query.getEndDate()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <E extends Enum<E>> java.util.Optional<E> parseEnum(Class<E> type, String raw) {
        try {
            return java.util.Optional.of(Enum.valueOf(type, raw.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private SecurityAuditLogDTO toDTO(SecurityAuditLogEntity entity) {
        SecurityAuditLogDTO dto = new SecurityAuditLogDTO();
        dto.setId(entity.getId() != null ? entity.getId().toString() : null);
        dto.setSequenceNumber(entity.getSequenceNumber());
        dto.setPreviousHash(entity.getPreviousHash());
        dto.setCurrentHash(entity.getCurrentHash());
        dto.setUserId(entity.getUserId());
        dto.setUsername(entity.getUsername());
        dto.setSessionId(entity.getSessionId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setUserAgent(entity.getUserAgent());
        dto.setCorrelationId(entity.getCorrelationId());
        dto.setRequestPath(entity.getRequestPath());
        dto.setDecisionReason(entity.getDecisionReason());
        dto.setEventType(entity.getEventType() != null ? entity.getEventType().name() : null);
        dto.setCategory(entity.getCategory() != null ? entity.getCategory().name() : null);
        dto.setContextData(entity.getContextData());
        dto.setTimestamp(entity.getTimestamp() != null ? entity.getTimestamp().format(FORMATTER) : null);
        return dto;
    }
}
