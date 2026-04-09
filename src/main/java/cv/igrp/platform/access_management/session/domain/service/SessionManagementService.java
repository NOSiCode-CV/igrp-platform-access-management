package cv.igrp.platform.access_management.session.domain.service;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.session.application.dto.SessionInitRequestDTO;
import cv.igrp.platform.access_management.session.application.dto.SessionRefreshRequestDTO;
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
import cv.igrp.platform.access_management.session.domain.service.SessionCacheService;
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
    public Optional<SessionResponseDTO> getCurrentSession(String userExternalId) {
        log.debug("Getting current session for user: {}", userExternalId);

        // First check cache
        SessionResponseDTO cachedSession = sessionCacheService.getOrLoadSession(userExternalId);
        if (cachedSession != null && sessionCacheService.isFromCache()) {
            log.debug("Session found in cache for user: {}", userExternalId);
            return Optional.of(cachedSession);
        }

        // Check database for active session
        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserExternalIdAndStatus(userExternalId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session found for user: {}", userExternalId);
            return Optional.empty();
        }

        SessionEntity session = sessionOpt.get();

        // Check if session is expired
        if (session.isExpired()) {
            log.info("Session expired for user: {}, marking as expired", userExternalId);
            session.expire();
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(userExternalId);
            return Optional.empty();
        }

        // Build response
        SessionResponseDTO response = buildSessionResponse(session, userExternalId);
        sessionCacheService.cacheSession(userExternalId, response);
        return Optional.of(response);
    }

    /**
     * Initialize a new session for user
     */
    public SessionResponseDTO initializeSession(String userExternalId, String clientIp, 
                                           String userAgent, String deviceId) {
        log.info("Initializing session for user: {}", userExternalId);

        // Validate user exists and is active
        IGRPUserEntity user = userRepository.findByExternalId(userExternalId)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND,
                        "USER_NOT_FOUND",
                        "User not found with external ID: " + userExternalId
                ));
        if (!Status.ACTIVE.equals(user.getStatus())) {
            log.warn("Inactive user attempting to create session: {}", userExternalId);
            throw IgrpResponseStatusException.of(
                    HttpStatus.FORBIDDEN,
                    "Inactive User",
                    "User account is not active");
        }

        // Close any existing active session
        sessionRepository.findByUserExternalIdAndStatus(userExternalId, SessionStatus.ACTIVE)
                .ifPresent(existingSession -> {
                    log.info("Closing existing session for user: {}", userExternalId);
                    existingSession.close("SESSION_REPLACED", "SYSTEM");
                    sessionRepository.save(existingSession);
                });

        // Create new session
        SessionEntity newSession = new SessionEntity();
        newSession.setSessionId(UUID.randomUUID());
        newSession.setUserExternalId(userExternalId);
        newSession.setStatus(SessionStatus.ACTIVE);
        newSession.setStartedAt(Instant.now());
        newSession.setLastSeenAt(Instant.now());
        newSession.setExpiresAt(Instant.now().plusSeconds(sessionProperties.getTimeoutSeconds()));
        newSession.setClientIp(clientIp);
        newSession.setUserAgentHash(hashUserAgent(userAgent));
        newSession.setDeviceId(deviceId);

        SessionEntity savedSession = sessionRepository.save(newSession);
        log.info("Created new session {} for user: {}", savedSession.getSessionId(), userExternalId);

        // Evict cache for this user
        sessionCacheEvictService.evictBySubject(userExternalId);

        return buildSessionResponse(savedSession, userExternalId);
    }

    /**
     * Refresh existing session
     */
    public Optional<SessionResponseDTO> refreshSession(String userExternalId, Integer extensionSeconds) {
        log.debug("Refreshing session for user: {}", userExternalId);

        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserExternalIdAndStatus(userExternalId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session to refresh for user: {}", userExternalId);
            return Optional.empty();
        }

        SessionEntity session = sessionOpt.get();

        // Check if session is expired
        if (session.isExpired()) {
            log.info("Cannot refresh expired session for user: {}", userExternalId);
            session.expire();
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(userExternalId);
            return Optional.empty();
        }

        // Extend session
        long extension = extensionSeconds != null ? extensionSeconds : sessionProperties.getTimeoutSeconds();
        Instant newExpiresAt = Instant.now().plusSeconds(extension);
        session.refresh(newExpiresAt);
        sessionRepository.save(session);

        log.info("Extended session {} for user: {} until {}", 
                session.getSessionId(), userExternalId, newExpiresAt);

        // Evict cache to force refresh
        sessionCacheEvictService.evictBySubject(userExternalId);

        return Optional.of(buildSessionResponse(session, userExternalId));
    }

    /**
     * Close current session
     */
    public boolean closeSession(String userExternalId, String reason) {
        log.info("Closing session for user: {} with reason: {}", userExternalId, reason);

        Optional<SessionEntity> sessionOpt = sessionRepository
                .findByUserExternalIdAndStatus(userExternalId, SessionStatus.ACTIVE);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session to close for user: {}", userExternalId);
            return false;
        }

        SessionEntity session = sessionOpt.get();
        session.close(reason, "USER");
        sessionRepository.save(session);

        // Evict cache
        sessionCacheEvictService.evictBySubject(userExternalId);

        log.info("Closed session {} for user: {}", session.getSessionId(), userExternalId);
        return true;
    }

    /**
     * Rotate session (close current and create new one)
     */
    public Optional<SessionResponseDTO> rotateSession(String userExternalId, String clientIp, 
                                                 String userAgent, String deviceId) {
        log.info("Rotating session for user: {}", userExternalId);

        // Close existing session
        boolean closed = closeSession(userExternalId, "SESSION_ROTATION");
        
        // Create new session
        SessionResponseDTO newSession = initializeSession(userExternalId, clientIp, userAgent, deviceId);
        
        log.info("Session rotation completed for user: {}, old session closed: {}", 
                userExternalId, closed);
        
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

        // Evict cache for the user
        sessionCacheEvictService.evictBySubject(session.getUserExternalId());

        log.info("Killed session {} for user: {}", sessionId, session.getUserExternalId());
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
                .map(session -> buildSessionResponse(session, session.getUserExternalId()))
                .collect(Collectors.toList());

        return new PageImpl<>(sessionDTOs, pageable, sessionPage.getTotalElements());
    }

    /**
     * Build session response DTO from entity
     */
    private SessionResponseDTO buildSessionResponse(SessionEntity session, String userExternalId) {
        // Get user information
        IGRPUserEntity user = userRepository.findByExternalId(userExternalId)
                .orElse(null);

        IGRPUserDTO userProfile = null;
        RoleDepartmentDTO currentActiveRole = null;
        List<RoleDepartmentDTO> roles = Collections.emptyList();
        List<CodeDescriptionDTO> departments = Collections.emptyList();

        if (user != null) {
            // Map user profile
            userProfile = new IGRPUserDTO();
            // Don't set internal ID - use external ID from UserIdentity interface
            userProfile.setName(user.getName());
            userProfile.setUsername(user.getUsername());
            userProfile.setEmail(user.getEmail());
            userProfile.setStatus(user.getStatus());
            userProfile.setPicture(user.getPicture());
            userProfile.setSignature(user.getSignature());

            // Map current active role
            if (user.getActiveRole() != null) {
                RoleDTO fullRoleDto = roleMapper.mapToDto(user.getActiveRole());
                currentActiveRole = new RoleDepartmentDTO(
                    fullRoleDto.getCode(),
                    fullRoleDto.getDepartmentCode()
                );
            }

            // Map all roles
            roles = Optional.ofNullable(user.getRoles()).orElse(Collections.emptyList())
                    .stream()
                    .filter(role -> Status.ACTIVE.equals(role.getStatus()))
                    .map(roleMapper::mapToDto)
                    .filter(Objects::nonNull)
                    .map(roleDto -> new RoleDepartmentDTO(roleDto.getCode(), roleDto.getDepartmentCode()))
                    .collect(Collectors.toList());

            // Extract departments from roles
            departments = roles.stream()
                    .map(RoleDepartmentDTO::departmentCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(deptCode -> new CodeDescriptionDTO(deptCode, deptCode)) // Using code as description for now
                    .collect(Collectors.toList());
        }

        // Build session response
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
        
        return sessions.map(session -> buildSessionResponse(session, session.getUserExternalId()));
    }

    /**
     * List sessions filtered by department only
     */
    @Transactional(readOnly = true)
    public Page<SessionResponseDTO> listSessionsByDepartment(String departmentCode, 
                                                           SessionStatus status, Pageable pageable) {
        log.debug("Listing sessions for department: {} with status: {}", departmentCode, status);

        Page<SessionEntity> sessions = sessionRepository.findByDepartment(departmentCode, status, pageable);
        
        return sessions.map(session -> buildSessionResponse(session, session.getUserExternalId()));
    }

    /**
     * Kill all sessions for users with a specific role (and optionally department)
     */
    @Transactional
    public int killSessionsByRole(String roleCode, String departmentCode, String reason, String killedBy) {
        log.info("Killing sessions for role: {} in department: {} by: {}", roleCode, departmentCode, killedBy);

        // Find all users with the specified role/department
        Set<String> userExternalIds = userRepository.findUserExternalIdsByRoleAndDepartment(roleCode, departmentCode);
        
        if (userExternalIds.isEmpty()) {
            log.info("No users found with role: {} in department: {}", roleCode, departmentCode);
            return 0;
        }

        // Kill all active sessions for these users
        int killedCount = sessionRepository.invalidateUserSessions(
                userExternalIds, SessionStatus.ACTIVE, SessionStatus.REVOKED, 
                Instant.now(), Instant.now(), reason, killedBy);

        // Evict from cache
        sessionCacheEvictService.evictBySubjects(userExternalIds);

        log.info("Killed {} sessions for role: {} in department: {}", killedCount, roleCode, departmentCode);
        return killedCount;
    }

    /**
     * Hash user agent for privacy
     */
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
