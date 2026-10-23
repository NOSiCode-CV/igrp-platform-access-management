package cv.igrp.platform.access_management.session.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for {@code t_user_session} — iGRP's own per-application sessions.
 *
 * <p><b>The explicit bean name is required, not cosmetic.</b> Spring Session's
 * {@code JdbcHttpSessionConfiguration} publishes its {@code JdbcIndexedSessionRepository}
 * under the name {@code sessionRepository}, which is exactly what Spring Data
 * would derive from this interface's name. With bean-definition overriding
 * disabled — the Boot default — the context fails to start:
 *
 * <pre>
 * The bean 'sessionRepository' ... could not be registered. A bean with that
 * name has already been defined in ... JdbcHttpSessionConfiguration
 * </pre>
 *
 * <p>Renaming this bean rather than the Spring Session one is the only option
 * available (theirs is library-defined), and it is also the better name: this
 * repository is about {@code t_user_session}, a different thing from the browser
 * servlet session in {@code SPRING_SESSION}. See the two-session note in
 * {@code CLAUDE.md}.
 *
 * <p>Nothing injects this by name — all call sites inject by type — so the name
 * is free to change. {@code SessionRepositoryBeanNameTest} guards it.
 */
@Repository("userSessionRepository")
public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    /**
     * List active sessions of a user ordered oldest-first by {@code last_seen_at}.
     * Used by the LRU eviction step when the per-user concurrency cap is exceeded.
     */
    List<SessionEntity> findByUserIdAndStatusOrderByLastSeenAtAsc(String userId,
                                                                  SessionStatus status);

    /**
     * List active sessions of a user ordered newest-first by {@code last_seen_at}.
     * Used by operations that need to pick "the most recent session" as a
     * best-effort fallback when the caller cannot supply the specific {@code sid}
     * to operate on (e.g. legacy code paths that only receive a userId).
     *
     * <p>Prefer {@link #findBySessionId(UUID)} when a JWT's {@code sid} claim
     * is available — that identifies the request's session unambiguously and
     * avoids the "which of the 5 tabs?" question.
     */
    List<SessionEntity> findByUserIdAndStatusOrderByLastSeenAtDesc(String userId,
                                                                   SessionStatus status);

    /**
     * Active sessions for a (user, device) pair, most recently seen first.
     *
     * <p><b>Returns a list, deliberately.</b> This used to return
     * {@code Optional} on the assumption that (user, device, ACTIVE) was unique
     * — one active session per physical device. That assumption no longer holds:
     * several applications sharing one browser each run their own
     * {@code authorization_code} exchange and so open their own session under
     * the same derived {@code device_id}. A single-result query here would throw
     * {@code NonUniqueResultException} the moment a user opened a second app.
     *
     * <p>Prefer {@link #findBySessionId(UUID)} whenever a JWT's {@code sid} is
     * available: with several sessions per device, only {@code sid} identifies
     * which one the request belongs to. This method is a fallback for callers
     * that have lost that anchor, and "most recently seen" is a heuristic, not
     * a correct answer.
     */
    List<SessionEntity> findByUserIdAndDeviceIdAndStatusOrderByLastSeenAtDesc(String userId,
                                                                              String deviceId,
                                                                              SessionStatus status);

    /**
     * Find the active session bound to the given JWT id. Used by the refresh
     * grant to recover the canonical {@code sid} from the rotating access token.
     */
    Optional<SessionEntity> findByJtiAndStatus(String jti, SessionStatus status);

    /**
     * Find session by session ID
     */
    Optional<SessionEntity> findBySessionId(UUID sessionId);

    /**
     * Find all sessions with a specific status
     */
    List<SessionEntity> findByStatus(SessionStatus status);

    /**
     * Find all sessions for a specific user
     */
    List<SessionEntity> findByUserId(String userId);

    /**
     * Find expired active sessions
     */
    @Query("SELECT s FROM SessionEntity s WHERE s.status = :status AND s.expiresAt < :now")
    List<SessionEntity> findExpiredSessions(@Param("status") SessionStatus status, @Param("now") Instant now);

    /**
     * Find all active sessions with pagination
     */
    Page<SessionEntity> findByStatus(SessionStatus status, Pageable pageable);

    /**
     * Find sessions by user IDs with pagination
     */
    @Query("SELECT s FROM SessionEntity s WHERE s.userId IN :userIds AND s.status = :status")
    Page<SessionEntity> findByUserIdsAndStatus(@Param("userIds") Set<String> userIds,
                                                @Param("status") SessionStatus status,
                                                Pageable pageable);

    /**
     * Find sessions by department (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userId = u.id " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode AND s.status = :status")
    Page<SessionEntity> findByDepartmentCodeAndStatus(@Param("departmentCode") String departmentCode,
                                                   @Param("status") SessionStatus status,
                                                   Pageable pageable);

    /**
     * Find sessions by role (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userId = u.id " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "WHERE r.code = :roleCode AND s.status = :status")
    Page<SessionEntity> findByRoleCodeAndStatus(@Param("roleCode") String roleCode,
                                              @Param("status") SessionStatus status,
                                              Pageable pageable);

    /**
     * Find sessions by role and department (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userId = u.id " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE r.code = :roleCode AND d.code = :departmentCode AND s.status = :status")
    Page<SessionEntity> findByRoleCodeAndDepartmentCodeAndStatus(@Param("roleCode") String roleCode,
                                                            @Param("departmentCode") String departmentCode,
                                                            @Param("status") SessionStatus status,
                                                            Pageable pageable);

    /**
     * Get user IDs for users with a specific role/department
     */
    @Query("SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE r.code = :roleCode AND d.code = :departmentCode")
    Set<String> findUserIdsByRole(@Param("roleCode") String roleCode, @Param("departmentCode") String departmentCode);

    /**
     * Get user IDs for users with roles in a specific department
     */
    @Query("SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode")
    Set<String> findUserIdsByDepartment(@Param("departmentCode") String departmentCode);

    /**
     * Count active sessions by user
     */
    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.userId = :userId AND s.status = :status")
    long countActiveSessionsByUser(@Param("userId") String userId, @Param("status") SessionStatus status);

    /**
     * Mark sessions as expired for a set of users
     */
    @Modifying
    @Transactional
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userId IN :userIds AND s.status = :oldStatus")
    int invalidateUserSessions(@Param("userIds") Set<String> userIds,
                           @Param("oldStatus") SessionStatus oldStatus,
                           @Param("newStatus") SessionStatus newStatus,
                           @Param("endedAt") Instant endedAt,
                           @Param("lastSeenAt") Instant lastSeenAt,
                           @Param("closedReason") String closedReason,
                           @Param("closedBy") String closedBy);

    /**
     * Mark sessions as expired by role
     */
    @Modifying
    @Transactional
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userId IN (SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r JOIN r.department d WHERE r.code = :roleCode AND d.code = :departmentCode) " +
           "AND s.status = :oldStatus")
    int invalidateSessionsByRole(@Param("roleCode") String roleCode,
                               @Param("departmentCode") String departmentCode,
                               @Param("oldStatus") SessionStatus oldStatus,
                               @Param("newStatus") SessionStatus newStatus,
                               @Param("endedAt") Instant endedAt,
                               @Param("lastSeenAt") Instant lastSeenAt,
                               @Param("closedReason") String closedReason,
                               @Param("closedBy") String closedBy);

    /**
     * Mark sessions as expired by department
     */
    @Modifying
    @Transactional
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userId IN (SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r JOIN r.department d WHERE d.code = :departmentCode) " +
           "AND s.status = :oldStatus")
    int invalidateSessionsByDepartment(@Param("departmentCode") String departmentCode,
                                    @Param("oldStatus") SessionStatus oldStatus,
                                    @Param("newStatus") SessionStatus newStatus,
                                    @Param("endedAt") Instant endedAt,
                                    @Param("lastSeenAt") Instant lastSeenAt,
                                    @Param("closedReason") String closedReason,
                                    @Param("closedBy") String closedBy);

    /**
     * Delete old sessions that have been closed/expired/revoked for a long time
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM SessionEntity s WHERE s.status IN :statuses AND s.endedAt < :cutoffDate")
    int deleteOldSessions(@Param("statuses") List<SessionStatus> statuses, @Param("cutoffDate") Instant cutoffDate);

    /**
     * Count sessions by status
     */
    long countByStatus(SessionStatus status);

    /**
     * Find sessions by role and optionally by department
     */
    @Query("SELECT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userId = u.id " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "WHERE r.code = :roleCode " +
           "AND (:departmentCode IS NULL OR r.department.code = :departmentCode) " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<SessionEntity> findByRoleAndDepartment(@Param("roleCode") String roleCode,
                                                 @Param("departmentCode") String departmentCode,
                                                 @Param("status") SessionStatus status,
                                                 Pageable pageable);

    /**
     * Find sessions by department only
     */
    @Query("SELECT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userId = u.id " +
           "JOIN u.userRoleAssignments ura JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<SessionEntity> findByDepartment(@Param("departmentCode") String departmentCode,
                                         @Param("status") SessionStatus status,
                                         Pageable pageable);
}
