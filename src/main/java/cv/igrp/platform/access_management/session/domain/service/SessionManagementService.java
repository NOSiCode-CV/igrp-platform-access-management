package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.session.application.dto.SessionResponseDTO;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.config.SessionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.Base64;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class SessionManagementService {

    private final SessionRepository sessionRepository;
    private final IGRPUserEntityRepository userRepository;
    private final RoleMapper roleMapper;
    private final SessionCacheService sessionCacheService;
    private final SessionCacheEvictService sessionCacheEvictService;
    private final SessionProperties sessionProperties;

    public SessionManagementService(
            SessionRepository sessionRepository,
            IGRPUserEntityRepository userRepository,
            RoleMapper roleMapper,
            SessionCacheService sessionCacheService,
            SessionCacheEvictService sessionCacheEvictService,
            SessionProperties sessionProperties) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.roleMapper = roleMapper;
        this.sessionCacheService = sessionCacheService;
        this.sessionCacheEvictService = sessionCacheEvictService;
        this.sessionProperties = sessionProperties;
    }

    /**
     * Get current session for user
     */
    @Transactional(readOnly = true)
    public Optional<SessionResponseDTO> getCurrentSession(String userId) {
        log.debug("Getting current session for user: {}", userId);

        // First check cache
        SessionResponseDTO cachedSession = sessionCacheService.getOrLoadSession(userId);
        if (cachedSession != null && sessionCacheService.isFromCache()) {
            log.debug("Session found in cache for user: {}", userId);
            return Optional.of(cachedSession);
        }

        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserIdAndStatus(userId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session found for user: {}", userId);
            return Optional.empty();
        }

        SessionEntity session = sessionOpt.get();

        if (session.isExpired()) {
            log.info("Session expired for user: {}, marking as expired", userId);
            session.expire();
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(userId);
            return Optional.empty();
        }

        SessionResponseDTO response = buildSessionResponse(session, userId);
        sessionCacheService.cacheSession(userId, response);
        return Optional.of(response);
    }

    /**
     * Initialize a new session for user
     */
    public SessionResponseDTO initializeSession(String userId, String clientIp,
                                           String userAgent, String deviceId) {
        log.info("Initializing session for user: {}", userId);

        IGRPUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found with id: " + userId
                ));
        if (!Status.ACTIVE.equals(user.getStatus())) {
            log.warn("Inactive user attempting to create session: {}", userId);
            throw IgrpResponseStatusException.of(
                    HttpStatus.FORBIDDEN,
                    "Inactive User",
                    "User account is not active");
        }

        sessionRepository.findByUserIdAndStatus(userId, SessionStatus.ACTIVE)
                .ifPresent(existingSession -> {
                    log.info("Closing existing session for user: {}", userId);
                    existingSession.close("SESSION_REPLACED", "SYSTEM");
                    sessionRepository.save(existingSession);
                });

        SessionEntity newSession = new SessionEntity();
        newSession.setSessionId(UUID.randomUUID());
        newSession.setUserId(userId);
        newSession.setStatus(SessionStatus.ACTIVE);
        newSession.setStartedAt(Instant.now());
        newSession.setLastSeenAt(Instant.now());
        newSession.setExpiresAt(Instant.now().plusSeconds(sessionProperties.getTimeoutSeconds()));
        newSession.setClientIp(clientIp);
        newSession.setUserAgentHash(hashUserAgent(userAgent));
        newSession.setDeviceId(deviceId);

        SessionEntity savedSession = sessionRepository.save(newSession);
        log.info("Created new session {} for user: {}", savedSession.getSessionId(), userId);

        sessionCacheEvictService.evictBySubject(userId);

        return buildSessionResponse(savedSession, userId);
    }

    /**
     * Refresh existing session
     */
    public Optional<SessionResponseDTO> refreshSession(String userId, Integer extensionSeconds) {
        log.debug("Refreshing session for user: {}", userId);

        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserIdAndStatus(userId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session to refresh for user: {}", userId);
            return Optional.empty();
        }

        SessionEntity session = sessionOpt.get();

        if (session.isExpired()) {
            log.info("Cannot refresh expired session for user: {}", userId);
            session.expire();
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(userId);
            return Optional.empty();
        }

        long extension = extensionSeconds != null ? extensionSeconds : sessionProperties.getTimeoutSeconds();
        Instant newExpiresAt = Instant.now().plusSeconds(extension);
        session.refresh(newExpiresAt);
        sessionRepository.save(session);

        log.info("Extended session {} for user: {} until {}",
                session.getSessionId(), userId, newExpiresAt);

        sessionCacheEvictService.evictBySubject(userId);

        return Optional.of(buildSessionResponse(session, userId));
    }

    /**
     * Close current session
     */
    public boolean closeSession(String userId, String reason) {
        log.info("Closing session for user: {} with reason: {}", userId, reason);

        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserIdAndStatus(userId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session to close for user: {}", userId);
            return false;
        }

        SessionEntity session = sessionOpt.get();
        session.close(reason, "USER");
        sessionRepository.save(session);

        sessionCacheEvictService.evictBySubject(userId);

        log.info("Closed session {} for user: {}", session.getSessionId(), userId);
        return true;
    }

    /**
     * Rotate session (close current and create new one)
     */
    public Optional<SessionResponseDTO> rotateSession(String userId, String clientIp,
                                                 String userAgent, String deviceId) {
        log.info("Rotating session for user: {}", userId);

        boolean closed = closeSession(userId, "SESSION_ROTATION");

        SessionResponseDTO newSession = initializeSession(userId, clientIp, userAgent, deviceId);

        log.info("Session rotation completed for user: {}, old session closed: {}",
                userId, closed);

        return Optional.of(newSession);
    }

    /**
     * Kill specific session (admin operation)
     */
    public boolean killSession(UUID sessionId, String reason, String killedBy) {
        log.info("Killing session {} by {} with reason: {}", sessionId, killedBy, reason);

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionId(sessionId);

        if (sessionOpt.isEmpty()) {
            log.warn("Session not found for killing: {}", sessionId);
            return false;
        }

        SessionEntity session = sessionOpt.get();
        session.revoke(reason, killedBy);
        sessionRepository.save(session);

        sessionCacheEvictService.evictBySubject(session.getUserId());

        log.info("Killed session {} for user: {}", sessionId, session.getUserId());
        return true;
    }

    /**
     * List sessions with pagination
     */
    @Transactional(readOnly = true)
    public Page<SessionResponseDTO> listSessions(SessionStatus status, Pageable pageable) {
        log.debug("Listing sessions with status: {}", status);

        Page<SessionEntity> sessionPage = sessionRepository.findByStatus(status, pageable);

        List<SessionResponseDTO> sessionDTOs = sessionPage.getContent().stream()
                .map(session -> buildSessionResponse(session, session.getUserId()))
                .collect(Collectors.toList());

        return new PageImpl<>(sessionDTOs, pageable, sessionPage.getTotalElements());
    }

    /**
     * Build session response DTO from entity
     */
    private SessionResponseDTO buildSessionResponse(SessionEntity session, String userId) {
        IGRPUserEntity user = userId != null ? userRepository.findById(userId).orElse(null) : null;

        IGRPUserDTO userProfile = null;
        RoleDepartmentDTO currentActiveRole = null;
        List<RoleDepartmentDTO> roles = Collections.emptyList();
        List<CodeDescriptionDTO> departments = Collections.emptyList();

        if (user != null) {
            userProfile = new IGRPUserDTO();
            userProfile.setName(user.getName());
            userProfile.setUsername(user.getUsername());
            userProfile.setEmail(user.getEmail());
            userProfile.setStatus(user.getStatus());
            userProfile.setPicture(user.getPicture());
            userProfile.setSignature(user.getSignature());

            if (user.getActiveRole() != null) {
                RoleDTO fullRoleDto = roleMapper.mapToDto(user.getActiveRole());
                currentActiveRole = new RoleDepartmentDTO(
                    fullRoleDto.getCode(),
                    fullRoleDto.getDepartmentCode()
                );
            }

            roles = user.getUserRoleAssignments().stream()
                    .filter(ura -> ura.getExpiresAt() == null || ura.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                    .filter(ura -> Status.ACTIVE.equals(ura.getRole().getStatus()))
                    .map(roleMapper::mapToDto)
                    .filter(Objects::nonNull)
                    .map(roleDto -> new RoleDepartmentDTO(roleDto.getCode(), roleDto.getDepartmentCode()))
                    .collect(Collectors.toList());

            departments = roles.stream()
                    .map(RoleDepartmentDTO::departmentCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(deptCode -> new CodeDescriptionDTO(deptCode, deptCode))
                    .collect(Collectors.toList());
        }

        SessionResponseDTO response = new SessionResponseDTO();
        response.setSessionId(session.getSessionId());
        response.setStatus(session.getStatus());
        response.setStartedAt(session.getStartedAt());
        response.setLastSeenAt(session.getLastSeenAt());
        response.setExpiresAt(session.getExpiresAt());
        response.setEndedAt(session.getEndedAt());
        response.setClosedReason(session.getClosedReason());
        response.setClosedBy(session.getClosedBy());
        response.setClientIp(session.getClientIp());
        response.setDeviceId(session.getDeviceId());
        response.setUserProfile(userProfile);
        response.setCurrentActiveRole(currentActiveRole);
        response.setRoles(roles);
        response.setDepartments(new ArrayList<>(departments));

        return response;
    }

    /**
     * List sessions filtered by role and optionally by department
     */
    @Transactional(readOnly = true)
    public Page<SessionResponseDTO> listSessionsByRole(String roleCode, String departmentCode,
                                                       SessionStatus status, Pageable pageable) {
        log.debug("Listing sessions for role: {} in department: {} with status: {}",
                 roleCode, departmentCode, status);

        Page<SessionEntity> sessions = sessionRepository.findByRoleAndDepartment(roleCode, departmentCode, status, pageable);

        return sessions.map(session -> buildSessionResponse(session, session.getUserId()));
    }

    /**
     * List sessions filtered by department only
     */
    @Transactional(readOnly = true)
    public Page<SessionResponseDTO> listSessionsByDepartment(String departmentCode,
                                                           SessionStatus status, Pageable pageable) {
        log.debug("Listing sessions for department: {} with status: {}", departmentCode, status);

        Page<SessionEntity> sessions = sessionRepository.findByDepartment(departmentCode, status, pageable);

        return sessions.map(session -> buildSessionResponse(session, session.getUserId()));
    }

    /**
     * Kill all sessions for users with a specific role (and optionally department)
     */
    @Transactional
    public int killSessionsByRole(String roleCode, String departmentCode, String reason, String killedBy) {
        log.info("Killing sessions for role: {} in department: {} by: {}", roleCode, departmentCode, killedBy);

        Set<String> userIds = userRepository.findUserIdsByRoleAndDepartment(roleCode, departmentCode);

        if (userIds.isEmpty()) {
            log.info("No users found with role: {} in department: {}", roleCode, departmentCode);
            return 0;
        }

        int killedCount = sessionRepository.invalidateUserSessions(
                userIds, SessionStatus.ACTIVE, SessionStatus.REVOKED,
                Instant.now(), Instant.now(), reason, killedBy);

        sessionCacheEvictService.evictBySubjects(userIds);

        log.info("Killed {} sessions for role: {} in department: {}", killedCount, roleCode, departmentCode);
        return killedCount;
    }

    private String hashUserAgent(String userAgent) {
        if (userAgent == null || userAgent.trim().isEmpty()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to hash user agent", e);
            return null;
        }
    }
}
