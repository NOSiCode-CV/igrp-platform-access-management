package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.application.dto.AuthAuditDTO;
import cv.igrp.platform.access_management.oauth_server.domain.models.AuthEventType;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.AuthAuditLogEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.AuthAuditLogJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service("oauthServerAuthAuditService")
public class AuthAuditService {

    private final AuthAuditLogJpaRepository repository;

    public AuthAuditService(AuthAuditLogJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(String username, AuthEventType eventType) {
        AuthAuditLogEntity entry = new AuthAuditLogEntity();
        entry.setId(UUID.randomUUID());
        entry.setUsername(username);
        entry.setEventType(eventType);
        entry.setTimestamp(LocalDateTime.now());

        HttpServletRequest request = currentRequest();
        if (request != null) {
            entry.setIpAddress(clientIp(request));
            entry.setUserAgent(request.getHeader("User-Agent"));
            try {
                entry.setSessionId(request.getRequestedSessionId());
            } catch (IllegalStateException ignored) {
                // no session in stateless context
            }
        }
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuthAuditDTO> getAllLogs() {
        return repository.findAllByOrderByTimestampDesc().stream().map(AuthAuditService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuthAuditDTO> getLogsByUser(String username) {
        return repository.findByUsernameOrderByTimestampDesc(username).stream().map(AuthAuditService::toDto).toList();
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    static AuthAuditDTO toDto(AuthAuditLogEntity entity) {
        return AuthAuditDTO.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .eventType(entity.getEventType())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .sessionId(entity.getSessionId())
                .timestamp(entity.getTimestamp())
                .build();
    }
}
