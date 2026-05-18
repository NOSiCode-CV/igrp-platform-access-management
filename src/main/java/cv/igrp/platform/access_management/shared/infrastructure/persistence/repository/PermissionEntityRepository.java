package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface PermissionEntityRepository extends
        JpaRepository<PermissionEntity, Integer>,
        JpaSpecificationExecutor<PermissionEntity>,
        RevisionRepository<PermissionEntity, Integer, Integer> {

    /**
     * Retrieves all permissions with a status included in the given list.
     *
     * @param statusList the list of {@link Status} values to filter by
     * @return a list of matching {@link PermissionEntity} entities
     */
    List<PermissionEntity> findByStatusIn(List<Status> statusList);

    /**
     * Finds a permission by its ID, excluding the specified status (commonly {@link Status#DELETED}).
     *
     * @param id     the ID of the permission
     * @param status the status to exclude
     * @return an {@link Optional} containing the {@link PermissionEntity}, if found and not matching the excluded status
     */
    Optional<PermissionEntity> findByIdAndStatusNot(Integer id, Status status);

    Optional<PermissionEntity> findByNameAndStatusNot(String name, Status status);

    default PermissionEntity findByNameAndStatusNotDeleted(String name) {
        return findByNameAndStatusNot(name, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_PERMISSION_NOT_FOUND_BY_NAME, name));
    }

    List<PermissionEntity> findAllByNameIn(List<String> name);

    List<PermissionEntity> findAllByNameInAndStatusNot(List<String> name, Status status);

    default List<PermissionEntity> findAllByNameInAndStatusNotDeleted(List<String> name) {
        return findAllByNameInAndStatusNot(name, Status.DELETED);
    }

    @Query("""
                SELECT DISTINCT p
               FROM PermissionEntity p
               WHERE ((
                   1=1 AND
                   NOT EXISTS (
                       SELECT 1
                       FROM RoleEntity r
                       WHERE r.code = :code AND r.parent IS NOT NULL
                   )
                   AND p.id IN (
                       SELECT dp.id
                       FROM RoleEntity r2
                       JOIN r2.department d
                       JOIN d.permissions dp
                       WHERE r2.code = :code
                   )
                   AND p.id NOT IN (
                       SELECT rp1.id
                       FROM RoleEntity r3
                       JOIN r3.permissions rp1
                       WHERE r3.code = :code
                   )
               )
               OR
                   EXISTS (
                       SELECT 1
                       FROM RoleEntity r
                       WHERE r.code = :code AND r.parent IS NOT NULL
                   )
                   AND p.id IN (
                       SELECT pp.id
                       FROM RoleEntity child
                       JOIN child.parent pr
                       JOIN pr.permissions pp
                       WHERE child.code = :code
                   )
                   AND p.id NOT IN (
                       SELECT rp2.id
                       FROM RoleEntity r4
                       JOIN r4.permissions rp2
                       WHERE r4.code = :code
                   )
               )
               AND p.status = 'ACTIVE'
            """)
    List<PermissionEntity> findAvailablePermissionsForRole(@Param("code") String code, @Param("system_permission") String systemPermission);

    @Query("""
                SELECT DISTINCT p
                FROM PermissionEntity p
                JOIN p.resources r
                WHERE ((
                        1=1 AND
                        NOT EXISTS (
                            SELECT 1
                            FROM DepartmentEntity d
                            WHERE d.code = :code AND d.parentId IS NOT NULL
                        )
                        AND p.id IN (
                            SELECT rp.id
                            FROM DepartmentEntity d2
                            JOIN d2.resources r2
                            JOIN r2.permissions rp
                            WHERE d2.code = :code
                        )
                        AND p.id NOT IN (
                            SELECT dpExplicit.id
                            FROM DepartmentEntity d3
                            JOIN d3.permissions dpExplicit
                            WHERE d3.code = :code
                        )
                    )
                    OR EXISTS (
                            SELECT 1
                            FROM DepartmentEntity d4
                            WHERE d4.code = :code AND d4.parentId IS NOT NULL
                        )
                        AND p.id IN (
                            SELECT pp.id
                            FROM DepartmentEntity child
                            JOIN child.parentId parent
                            JOIN parent.resources pr
                            JOIN pr.permissions pp
                            WHERE child.code = :code
                        )
                        AND p.id NOT IN (
                            SELECT dpChildExplicit.id
                            FROM DepartmentEntity d5
                            JOIN d5.permissions dpChildExplicit
                            WHERE d5.code = :code
                        )
                    )
                AND p.status = 'ACTIVE'
                AND (
                            :resourceName IS NULL
                            OR r.name = :resourceName
                        )
            """)
    List<PermissionEntity> findAvailablePermissionsForDepartment(
            @Param("code") String code,
            @Param("system_permission") String systemPermission,
            @Param("resourceName") String resourceName
    );

    @Query(
        """
                SELECT p
                FROM PermissionEntity p
                JOIN p.departments d
                WHERE d = :department
                AND p.status != :status
        """
    )
    List<PermissionEntity> findByDepartmentAndStatusNot(DepartmentEntity department, Status status);

    @Query(value = """
    SELECT p.*
    FROM t_permission p
    JOIN t_permission_department dp ON dp.permission_id = p.id
    WHERE dp.department = :department
      AND p.status <> :status
      AND (:name IS NULL OR p.name ILIKE CONCAT('%', :name, '%'))
""", nativeQuery = true)
    List<PermissionEntity> findByDepartmentAndStatusNotFiltered(Integer department, String status, String name);

    List<PermissionEntity> findAllByResourcesAndStatusNot(Set<ResourceEntity> resources, Status status);

    boolean existsByName(String name);

}