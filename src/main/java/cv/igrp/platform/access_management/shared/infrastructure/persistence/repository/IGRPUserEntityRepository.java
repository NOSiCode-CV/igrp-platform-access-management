package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface IGRPUserEntityRepository extends
        JpaRepository<IGRPUserEntity, Integer>,
        JpaSpecificationExecutor<IGRPUserEntity>,
        RevisionRepository<IGRPUserEntity, Integer, Integer> {

    @Query("""
                select u from IGRPUserEntity u
                left join fetch u.userRoleAssignments ura
                left join fetch ura.role r
                left join fetch r.permissions p
                where u.id = :id and u.status != 'DELETED'
            """)
    Optional<IGRPUserEntity> findByIdWithRolesAndPermissions(@Param("id") Integer id);

    /**
     * Lookup by the persisted external_id column. Used by OIDC federation
     * flows that need to map an external OpenID subject to an internal user
     * record. The column itself remains on the entity even though most lookup
     * paths have switched to the internal id.
     */
    @Query("""
                select u from IGRPUserEntity u
                where u.externalId = :externalId and u.status != 'DELETED'
            """)
    Optional<IGRPUserEntity> findByExternalId(@Param("externalId") String externalId);


    @Query("""
                select u from IGRPUserEntity u where u.username = :username and u.status != 'DELETED'
            """)
    Optional<IGRPUserEntity> findByUsername(String username);

    @Query("""
        select case when count(u) > 0 then true else false end from IGRPUserEntity u where u.username = :username and u.status != 'DELETED'
    """)
    boolean existsByUsername(String username);

    @Query("""
        select case when count(u) > 0 then true else false end from IGRPUserEntity u where u.email = :email and u.status != 'DELETED'
    """)
    boolean existsByEmail(@Param("email") String email);

    @Query("""
        select u from IGRPUserEntity u
        where lower(cast(u.email as string)) = lower(:email) and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByEmailIgnoreCase(@Param("email") String email);

    @Query("""
        select u from IGRPUserEntity u
        where upper(cast(u.nic as string)) = upper(cast(:nic as string)) and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByNicIgnoreCase(@Param("nic") String nic);

    @Query("""
        select u from IGRPUserEntity u
        where u.phoneNumber = :phoneNumber and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * Find user IDs by role and optionally by department
     */
    @Query("SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura " +
           "JOIN ura.role r " +
           "WHERE r.code = :roleCode " +
           "AND (:departmentCode IS NULL OR r.department.code = :departmentCode) " +
           "AND u.status != 'DELETED'")
    Set<Integer> findUserIdsByRoleAndDepartment(@Param("roleCode") String roleCode,
                                                      @Param("departmentCode") String departmentCode);

    /**
     * Find user IDs by department only
     */
    @Query("SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura " +
           "JOIN ura.role r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode " +
           "AND u.status != 'DELETED'")
    Set<Integer> findUserIdsByDepartment(@Param("departmentCode") String departmentCode);

    /**
     * Find user IDs holding a permission (by name) through any of their assigned roles.
     * Used by the session-invalidation cascade when a permission is deleted (Phase D8).
     */
    @Query("SELECT DISTINCT u.id FROM IGRPUserEntity u " +
           "JOIN u.userRoleAssignments ura " +
           "JOIN ura.role r " +
           "JOIN r.permissions p " +
           "WHERE p.name = :permissionName " +
           "AND u.status != 'DELETED'")
    Set<Integer> findUserIdsByPermissionName(@Param("permissionName") String permissionName);

    /**
     * Phase F1 — read just the {@code tokens_not_valid_before} floor for a user
     * without dragging the full entity into the persistence context. Hot-path
     * lookup from {@code SessionEnforcementFilter}.
     */
    @Query("SELECT u.tokensNotValidBefore FROM IGRPUserEntity u WHERE u.id = :id")
    Optional<java.time.Instant> findTokensNotValidBeforeById(@Param("id") Integer id);

    /**
     * Phase F1 — bump the user-wide token validity floor to {@code now}, used by
     * forced re-auth / password-reset flows. Bulk update so every concurrent JWT
     * for the user becomes invalid in a single statement.
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE IGRPUserEntity u SET u.tokensNotValidBefore = :now WHERE u.id = :id")
    int updateTokensNotValidBefore(@Param("id") Integer id, @Param("now") java.time.Instant now);

    @Query("""
        SELECT u FROM IGRPUserEntity u WHERE u.status != 'DELETED'
        AND (
            (:externalId IS NOT NULL AND u.externalId = :externalId)
            OR (:email IS NOT NULL AND lower(u.email) = lower(:email))
            OR (:nic IS NOT NULL AND upper(cast(u.nic as string)) = upper(cast(:nic as string)))
            OR (:phoneNumber IS NOT NULL AND u.phoneNumber = :phoneNumber)
            OR (:externalId IS NOT NULL AND u.username = :externalId)
        )
    """)
    Optional<IGRPUserEntity> findByAnyIdentifier(
            @Param("email") String email,
            @Param("externalId") String externalId,
            @Param("nic") String nic,
            @Param("phoneNumber") String phoneNumber
    );

}
