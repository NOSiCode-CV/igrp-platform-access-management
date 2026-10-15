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
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
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
     * Get current session for the caller.
     *
     * <p>When {@code sid} is non-null (populated from the JWT's {@code sid}
     * claim), this returns THE session this request is authenticated by —
     * unambiguous even when the user has multiple concurrent sessions
     * (LRU cap defaults to 5). When {@code sid} is null (legacy callers
     * without JWT context), falls back to the user's most-recently-seen
     * active session — best-effort.
     *
     * <p>Historical note: this method used to call
     * {@code SessionRepository.findByUserIdAndStatus(userId, ACTIVE)} which
     * returned {@code Optional<SessionEntity>}. That signature crashed with
     * {@code NonUniqueResultException} the moment any user held more than
     * one active session. The bad method has been removed; every code path
     * either resolves the specific session by {@code sid} or explicitly
     * chooses one from the list.
     */
    @Transactional(readOnly = true)
    public Optional<SessionResponseDTO> getCurrentSession(String userId, UUID sid) {
        log.debug("Getting current session for user: {} sid: {}", userId, sid);

        // First check cache
        SessionResponseDTO cachedSession = sessionCacheService.getOrLoadSession(userId);
        if (cachedSession != null && sessionCacheService.isFromCache()) {
            log.debug("Session found in cache for user: {}", userId);
            return Optional.of(cachedSession);
        }

        Optional<SessionEntity> sessionOpt = (sid != null)
                ? sessionRepository.findBySessionId(sid)
                        .filter(s -> userId.equals(s.getUserId()))
                        .filter(s -> s.getStatus() == SessionStatus.ACTIVE)
                : findMostRecentActive(userId);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session found for user: {} sid: {}", userId, sid);
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
     * Backward-compatible overload — used by callers that don't yet propagate
     * the JWT sid. Prefer {@link #getCurrentSession(String, UUID)} everywhere.
     */
    @Transactional(readOnly = true)
    public Optional<SessionResponseDTO> getCurrentSession(String userId) {
        return getCurrentSession(userId, null);
    }

    /**
     * Pick the user's most-recently-seen active session. Best-effort fallback
     * for legacy code paths that receive only a userId and can't disambiguate
     * across the (up to {@code IGRP_SESSION_MAX_PER_USER}) concurrent sessions.
     */
    private Optional<SessionEntity> findMostRecentActive(String userId) {
        return sessionRepository
                .findByUserIdAndStatusOrderByLastSeenAtDesc(userId, SessionStatus.ACTIVE)
                .stream()
                .findFirst();
    }

    /**
     * The session the CALLER is operating on, identified by the {@code sid}
     * claim of their own access token.
     *
     * <p>Several applications sharing one browser each hold their own session,
     * so "the user's most recently seen active session" is somebody else's row
     * as often as not. These endpoints used to resolve that way, which meant one
     * application refreshing or rotating could slide or close a sibling's
     * session. {@code sid} is the only value that says which one the request
     * belongs to.
     *
     * <p>The ownership check is belt-and-braces: {@code sid} is read from the
     * caller's own validated JWT, so it is already theirs — but a session
     * belonging to another subject must never be touched on the strength of a
     * claim, whatever the route in.
     *
     * <p>Falls back to the most recently seen session only when the token
     * carries no {@code sid} (M2M tokens, or one issued before sid existed).
     * That fallback is logged: with several apps per browser it is a guess.
     */
    private Optional<SessionEntity> resolveCallerSession(String userId, UUID sid) {
        if (sid == null) {
            log.warn("Session operation for user {} arrived without a sid claim — falling back to the "
                    + "most recently seen active session, which may belong to another application", userId);
            return findMostRecentActive(userId);
        }
        Optional<SessionEntity> found = sessionRepository.findBySessionId(sid);
        if (found.isEmpty()) {
            log.debug("No session row for sid={} (user {})", sid, userId);
            return Optional.empty();
        }
        SessionEntity session = found.get();
        if (!userId.equals(session.getUserId())) {
            log.warn("Refusing session operation: sid={} belongs to another subject than the caller {}",
                    sid, userId);
            return Optional.empty();
        }
        return found;
    }

    /**
     * Initialize a new session for user
     */
    public SessionResponseDTO initializeSession(String userId, String clientIp,
                                           String userAgent, String deviceId) {
        log.info("Initializing session for user: {}", userId);

        IGRPUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_ID, userId));
        if (!Status.ACTIVE.equals(user.getStatus())) {
            log.warn("Inactive user attempting to create session: {}", userId);
            throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_SESSION_USER_INACTIVE, userId);
        }

        // Pre-existing behaviour was "close THE existing session before opening a
        // new one" — that assumed one session per user. Under the LRU model (up
        // to IGRP_SESSION_MAX_PER_USER sessions) we preserve the closest legacy
        // semantic: close the most-recently-seen active session. Full multi-
        // session handling here belongs in SessionIssuanceService (which does
        // proper LRU eviction on issuance).
        findMostRecentActive(userId)
                .ifPresent(existingSession -> {
                    log.info("Closing most-recent existing session for user: {}", userId);
                    existingSession.close("SESSION_REPLACED", "SYSTEM");
                    sessionRepository.save(existingSession);
                });

        return createSession(userId, clientIp, userAgent, deviceId);
    }

    /**
     * Open a session row without closing anything first.
     *
     * <p>Split out of {@link #initializeSession} so {@code rotateSession} can
     * close exactly the caller's session and open a replacement, instead of
     * inheriting that method's "close the most-recently-seen row" step — which,
     * once several applications share a browser, closed a sibling application's
     * session as a side effect of one app rotating its own.
     */
    private SessionResponseDTO createSession(String userId, String clientIp,
                                             String userAgent, String deviceId) {
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
    public Optional<SessionResponseDTO> refreshSession(String userId, UUID sid, Integer extensionSeconds) {
        log.debug("Refreshing session for user: {} sid: {}", userId, sid);

        Optional<SessionEntity> sessionOpt = resolveCallerSession(userId, sid);

        if (sessionOpt.isEmpty()) {
            log.debug("No active session to refresh for user: {} sid: {}", userId, sid);
            return Optional.empty();
        }

        SessionEntity session = sessionOpt.get();

        // Resolving by sid returns the row whatever its state, where the old
        // most-recently-active lookup filtered to ACTIVE implicitly. A closed or
        // already-expired session must not be refreshed back into service.
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            log.info("Cannot refresh session {} for user {}: status is {}",
                    session.getSessionId(), userId, session.getStatus());
            return Optional.empty();
        }

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
    /**
     * Close the CALLER's own session, identified by the {@code sid} claim.
     *
     * <p>Server-side only. This ends the {@link SessionEntity} so
     * {@code SessionEnforcementFilter} rejects the next request carrying that
     * {@code sid} — it does NOT clear browser cookies, end the SSO session at
     * this server, revoke the caller's tokens, or cascade to the upstream IdP.
     * {@code /connect/logout} with an {@code id_token_hint} remains the way to
     * actually sign a user out; this is for an application that wants to drop
     * its own server-side session without touching the browser's login.
     *
     * @return true when a session was closed, false when the caller had none.
     */
    public boolean closeCallerSession(String userId, UUID sid, String reason) {
        log.info("Closing session for user: {} sid: {} reason: {}", userId, sid, reason);

        Optional<SessionEntity> sessionOpt = resolveCallerSession(userId, sid);
        if (sessionOpt.isEmpty()) {
            log.debug("No session to close for user: {} sid: {}", userId, sid);
            return false;
        }

        SessionEntity session = sessionOpt.get();
        if (!SessionStatus.ACTIVE.equals(session.getStatus())) {
            log.debug("Session {} is already {} — nothing to close", session.getSessionId(), session.getStatus());
            return false;
        }

        session.close(reason, "USER");
        sessionRepository.save(session);
        sessionCacheEvictService.evictBySubject(userId);
        log.info("Closed session {} for user {}", session.getSessionId(), userId);
        return true;
    }

    public boolean closeSession(String userId, String reason) {
        log.info("Closing session for user: {} with reason: {}", userId, reason);

        // Best-effort: close the most-recent active session. When the caller
        // has JWT context, prefer looking up by sid via findBySessionId and
        // closing that specific row.
        Optional<SessionEntity> sessionOpt = findMostRecentActive(userId);

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
    public Optional<SessionResponseDTO> rotateSession(String userId, UUID sid, String clientIp,
                                                 String userAgent, String deviceId) {
        log.info("Rotating session for user: {} sid: {}", userId, sid);

        // Close exactly the caller's session, then open a replacement.
        //
        // This previously ran closeSession(userId) followed by
        // initializeSession(userId), and BOTH resolved "the most recently seen
        // active session" — so one rotate closed two rows, neither of them
        // necessarily the caller's, and the new row was bound to no token. With
        // several applications in one browser that signed siblings out.
        Optional<SessionEntity> current = resolveCallerSession(userId, sid);
        current.ifPresent(session -> {
            session.close("SESSION_ROTATION", "USER");
            sessionRepository.save(session);
            sessionCacheEvictService.evictBySubject(userId);
            log.info("Closed session {} for rotation (user {})", session.getSessionId(), userId);
        });

        SessionResponseDTO newSession = createSession(userId, clientIp, userAgent, deviceId);

        log.info("Session rotation completed for user: {}, old session closed: {}",
                userId, current.isPresent());

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

            // .distinct() on URAs: the JPQL fetch used by findByIdWithRolesAndPermissions
            // joins u.userRoleAssignments with r.permissions in one query, producing a
            // Cartesian product. Each URA appears once per permission on its role
            // (same Hibernate-managed reference), so without dedup /api/session would
            // return a role N times where N = permissions on that role.
            roles = user.getUserRoleAssignments().stream()
                    .filter(ura -> ura.getExpiresAt() == null || ura.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
                    .filter(ura -> Status.ACTIVE.equals(ura.getRole().getStatus()))
                    .distinct()
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
