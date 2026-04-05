package cv.igrp.platform.access_management.shared.api.audit;

import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditLog;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AuthAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'SECURITY_AUDITOR')")
public class AuthAuditController {

    private final AuthAuditLogRepository repository;

    public AuthAuditController(AuthAuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<AuthAuditLogDTO> list(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDTO);
    }

    @GetMapping("/{id}")
    public AuthAuditLogDTO getById(@PathVariable UUID id) {
        return repository.findById(id).map(this::toDTO).orElse(null);
    }

    @GetMapping("/user/{userId}")
    public Page<AuthAuditLogDTO> getByUserId(@PathVariable String userId, Pageable pageable) {
        return repository.findByUserId(userId, pageable).map(this::toDTO);
    }

    private AuthAuditLogDTO toDTO(AuthAuditLog log) {
        return new AuthAuditLogDTO(
            log.getId(),
            log.getEventType(),
            log.getIdentifierType(),
            log.getIdentifierValue(),
            log.getUserId(),
            log.getApplicationCode(),
            log.getIpAddress(),
            log.getUserAgent(),
            log.getSessionId(),
            log.getFailureReason(),
            log.getTimestamp(),
            log.getEnvironment()
        );
    }
}
