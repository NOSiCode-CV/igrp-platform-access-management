package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface PermissionEntityRepository extends
    JpaRepository<PermissionEntity, Integer>,
    JpaSpecificationExecutor<PermissionEntity>,
    RevisionRepository<PermissionEntity, Integer, Integer>
{

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
     * @param id the ID of the permission
     * @param status the status to exclude
     * @return an {@link Optional} containing the {@link PermissionEntity}, if found and not matching the excluded status
     */
    Optional<PermissionEntity> findByIdAndStatusNot(Integer id, Status status);
    Optional<PermissionEntity> findByNameAndStatusNot(String name, Status status);

    List<PermissionEntity> findAllByNameIn(List<String> name);

    @Query("""
    SELECT DISTINCT p
    FROM PermissionEntity p
    WHERE (
        p.id IN (
            SELECT pp.id
            FROM RoleEntity r
            JOIN r.parent pr
            JOIN pr.permissions pp
            WHERE r.name = :name
        )
        OR p.id IN (
            SELECT cp.id
            FROM RoleEntity r
            JOIN r.children cr
            JOIN cr.permissions cp
            WHERE r.name = :name
        )
    )
    AND p.id NOT IN (
        SELECT rp.id
        FROM RoleEntity r2
        JOIN r2.permissions rp
        WHERE r2.name = :name
    )
""")
    List<PermissionEntity> findAvailablePermissionsForRole(@Param("name") String name);


}