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
        select u from IGRPUserEntity u where u.externalId = :externalId and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByExternalId(@Param("externalId") String externalId);

    @Query("""
                select u from IGRPUserEntity u
                left join fetch u.roles r
                left join fetch r.permissions p
                where u.externalId = :externalId and u.status != 'DELETED'
            """)
    Optional<IGRPUserEntity> findByExternalIdWithRolesAndPermissions(String externalId);


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

    /**
     * Find user external IDs by role and optionally by department
     */
    @Query("SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r " +
           "WHERE r.code = :roleCode " +
           "AND (:departmentCode IS NULL OR r.department.code = :departmentCode) " +
           "AND u.status != 'DELETED'")
    Set<String> findUserExternalIdsByRoleAndDepartment(@Param("roleCode") String roleCode,
                                                      @Param("departmentCode") String departmentCode);

    /**
     * Find user external IDs by department only
     */
    @Query("SELECT DISTINCT u.externalId FROM IGRPUserEntity u " +
           "JOIN u.roles r " +
           "JOIN r.department d " +
           "WHERE d.code = :departmentCode " +
           "AND u.status != 'DELETED'")
    Set<String> findUserExternalIdsByDepartment(@Param("departmentCode") String departmentCode);

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