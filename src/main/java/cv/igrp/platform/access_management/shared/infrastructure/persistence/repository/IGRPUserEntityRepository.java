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
    RevisionRepository<IGRPUserEntity, Integer, Integer>
{

    @Query("""
        select u from IGRPUserEntity u where u.externalId = :externalId and u.status != 'DELETED'
    """)
    Optional<IGRPUserEntity> findByExternalId(@Param("externalId") String externalId);


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

}