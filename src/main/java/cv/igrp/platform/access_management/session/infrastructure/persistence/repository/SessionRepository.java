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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, Long> {

    /**
     * Find active session for a specific user
     */
    Optional<SessionEntity> findByUserExternalIdAndStatus(String userExternalId, SessionStatus status);

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
    List<SessionEntity> findByUserExternalId(String userExternalId);

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
     * Find sessions by user external IDs with pagination
     */
    @Query("SELECT s FROM SessionEntity s WHERE s.userExternalId IN :userIds AND s.status = :status")
    Page<SessionEntity> findByUserExternalIdsAndStatus(@Param("userIds") Set<String> userIds, 
                                                   @Param("status") SessionStatus status, 
                                                   Pageable pageable);

    /**
     * Find sessions by department (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userExternalId = u.externalId " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode AND s.status = :status")
    Page<SessionEntity> findByDepartmentCodeAndStatus(@Param("departmentCode") String departmentCode, 
                                                   @Param("status") SessionStatus status, 
                                                   Pageable pageable);

    /**
     * Find sessions by role (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userExternalId = u.externalId " +
           "JOIN u.roles r " +
           "WHERE r.code = :roleCode AND s.status = :status")
    Page<SessionEntity> findByRoleCodeAndStatus(@Param("roleCode") String roleCode, 
                                              @Param("status") SessionStatus status, 
                                              Pageable pageable);

    /**
     * Find sessions by role and department (through user roles)
     */
    @Query("SELECT DISTINCT s FROM SessionEntity s " +
           "JOIN IGRPUserEntity u ON s.userExternalId = u.externalId " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE r.code = :roleCode AND d.code = :departmentCode AND s.status = :status")
    Page<SessionEntity> findByRoleCodeAndDepartmentCodeAndStatus(@Param("roleCode") String roleCode, 
                                                            @Param("departmentCode") String departmentCode, 
                                                            @Param("status") SessionStatus status, 
                                                            Pageable pageable);

    /**
     * Get user external IDs for users with a specific role
     */
    @Query("SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE r.code = :roleCode AND d.code = :departmentCode")
    Set<String> findUserIdsByRole(@Param("roleCode") String roleCode, @Param("departmentCode") String departmentCode);

    /**
     * Get user external IDs for users with roles in a specific department
     */
    @Query("SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode")
    Set<String> findUserIdsByDepartment(@Param("departmentCode") String departmentCode);

    /**
     * Count active sessions by user
     */
    @Query("SELECT COUNT(s) FROM SessionEntity s WHERE s.userExternalId = :userExternalId AND s.status = :status")
    long countActiveSessionsByUser(@Param("userExternalId") String userExternalId, @Param("status") SessionStatus status);

    /**
     * Mark sessions as expired for a set of users
     */
    @Modifying
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userExternalId IN :userIds AND s.status = :oldStatus")
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
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userExternalId IN (SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r JOIN r.department d WHERE r.code = :roleCode AND d.code = :departmentCode) " +
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
    @Query("UPDATE SessionEntity s SET s.status = :newStatus, s.endedAt = :endedAt, s.lastSeenAt = :lastSeenAt, " +
           "s.closedReason = :closedReason, s.closedBy = :closedBy " +
           "WHERE s.userExternalId IN (SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r JOIN r.department d WHERE d.code = :departmentCode) " +
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
           "JOIN IGRPUserEntity u ON s.userExternalId = u.externalId " +
           "JOIN u.roles r " +
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
           "JOIN IGRPUserEntity u ON s.userExternalId = u.externalId " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode " +
           "AND (:status IS NULL OR s.status = :status)")
    Page<SessionEntity> findByDepartment(@Param("departmentCode") String departmentCode,
                                         @Param("status") SessionStatus status,
                                         Pageable pageable);
}
