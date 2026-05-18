package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface ApplicationEntityRepository extends
        JpaRepository<ApplicationEntity, Integer>,
        JpaSpecificationExecutor<ApplicationEntity>,
        RevisionRepository<ApplicationEntity, Integer, Integer> {

    @Query("""
            SELECT DISTINCT a
            FROM ApplicationEntity a
            JOIN a.departments d
            JOIN d.roles r
            JOIN r.userRoleAssignments ura
            JOIN ura.user u
            WHERE a.status = :status
                   AND (u.id = :id OR u.email = :email)
                   AND r.department = d
            """)
    List<ApplicationEntity> findApplicationsByUserOrEmailAndStatus(
            @Param("id") Integer id,
            @Param("email") String email,
            @Param("status") Status status
    );

    @Query("""
                SELECT a FROM ApplicationEntity a
                     WHERE NOT EXISTS (
                         SELECT 1
                         FROM ApplicationEntity app
                         JOIN app.departments d
                         JOIN d.roles r
                         JOIN r.userRoleAssignments ura
                         JOIN ura.user u
                         WHERE app.id = a.id AND (u.id = :id OR u.email = :uid)
                     )
            """)
    List<ApplicationEntity> findDeniedApplications(@Param("id") Integer id, @Param("uid") String uid);

    // Case 1: Department is top-level, all applications that are not assigned will appear
    // Case 2: Application is inherited from the parent department for attribution
    // Exclude applications already attributed to this department or that it is not available
    @Query("""
                SELECT DISTINCT a
                     FROM ApplicationEntity a
                     WHERE ((
                             1=1 AND
                             NOT EXISTS (
                                 SELECT 1
                                 FROM DepartmentEntity d
                                 WHERE d.code = :code AND d.parentId IS NOT NULL
                             )
                             AND a.id NOT IN (
                                 SELECT da.id
                                 FROM DepartmentEntity d2
                                 JOIN d2.applications da
                                 WHERE d2.code = :code
                             )
                         )
                         OR
                             EXISTS (
                                 SELECT 1
                                 FROM DepartmentEntity child
                                 JOIN child.parentId p
                                 JOIN p.applications pa
                                 WHERE child.code = :code
                                   AND pa.id = a.id
                                   AND a.id NOT IN (
                                       SELECT da3.id
                                       FROM DepartmentEntity d3
                                       JOIN d3.applications da3
                                       WHERE d3.code = :code
                                   )
                             ))
                             AND a.status = 'ACTIVE'
            """)
    List<ApplicationEntity> findAvailableApplicationsForDepartment(@Param("code") String code);

    Optional<ApplicationEntity> findByCodeAndStatusNot(String code, Status status);

    default ApplicationEntity findByCodeAndStatusNotDeleted(String code) {
        return findByCodeAndStatusNot(code, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_APPLICATION_NOT_FOUND_BY_CODE, code));
    }

    List<ApplicationEntity> findByIdInAndStatusNot(Collection<Integer> ids, Status status);

    @Query("""
                select a.id
                from ApplicationEntity a
                join a.departments d
                where d.id in :departmentIds
            """)
    Set<Integer> findByDepartmentIds(Set<Integer> departmentIds);

    @Query("""
        select a
        from ApplicationEntity a
        join a.departments d
        where d = :department
        and a.status <> :status
    """)
    List<ApplicationEntity> findByDepartmentAndStatusNot(DepartmentEntity department, Status status);

    @Query("""
        select a
        from ApplicationEntity a
        join a.departments d
        where d = :department
        and a.status <> :status
        and (
            :applicationCode IS NULL
            OR a.code = :applicationCode
        )
    """)
    List<ApplicationEntity> findByDepartmentAndStatusNotFiltered(DepartmentEntity department, Status status, String applicationCode);

    @Query("""
        select a
        from ApplicationEntity a
        join a.departments d
        join d.roles r
        join r.userRoleAssignments ura
        join ura.user u
        where u = :user
        and a.status <> 'DELETED'
    """)
    List<ApplicationEntity> findByUserIdAndStatusNotDeleted(IGRPUserEntity user);

    @Query("""
        select a
        from ApplicationEntity a
        join a.departments d
        join d.roles r
        join r.userRoleAssignments ura
        join ura.user u
        where u = :user
        and a.status = 'ACTIVE'
    """)
    List<ApplicationEntity> findByUserIdAndStatusActive(IGRPUserEntity user);

    List<ApplicationEntity> findByStatus(Status status);

    default List<ApplicationEntity> findAllAndStatusActive() {
        return findByStatus(Status.ACTIVE);
    }

    @Query(value = """
    SELECT *
    FROM t_application a
    WHERE a.status = 'ACTIVE'
      AND (:code IS NULL OR a.code ILIKE '%' || :code || '%')
      AND (:name IS NULL OR a.name ILIKE '%' || :name || '%')
""", nativeQuery = true)
    List<ApplicationEntity> findAllActiveFiltered(
            @Param("code") String applicationCode,
            @Param("name") String applicationName
    );


    @Query(value = """
    SELECT DISTINCT a.*
    FROM t_application a
    JOIN t_department_application d ON d.application_id = a.id
    JOIN t_role r ON r.department = d.department_id
    JOIN t_user_role_assignment ura ON ura.role_id = r.id
    JOIN t_user u ON u.id = ura.user_id
    WHERE u.id = :userId
      AND a.status = 'ACTIVE'
      AND (:code IS NULL OR a.code ILIKE CONCAT('%', :code, '%'))
      AND (:name IS NULL OR a.name ILIKE CONCAT('%', :name, '%'))
""", nativeQuery = true)
    List<ApplicationEntity> findByUserAndActiveFiltered(
            @Param("userId") String userId,
            @Param("code") String applicationCode,
            @Param("name") String applicationName
    );

    @Query(value = """
    SELECT DISTINCT a.*
    FROM t_application a
    JOIN t_department_application d ON d.application_id = a.id
    JOIN t_role r ON r.department = d.department_id
    JOIN t_user_role_assignment ura ON ura.role_id = r.id
    JOIN t_user u ON u.id = ura.user_id
    WHERE u.id = :userId
      AND a.status = 'ACTIVE'
      AND r.id = u.active_role_id
      AND (:code IS NULL OR a.code ILIKE CONCAT('%', :code, '%'))
      AND (:name IS NULL OR a.name ILIKE CONCAT('%', :name, '%'))
""", nativeQuery = true)
    List<ApplicationEntity> findByCurrentUserAndActiveFiltered(
            @Param("userId") String userId,
            @Param("code") String applicationCode,
            @Param("name") String applicationName
    );


}