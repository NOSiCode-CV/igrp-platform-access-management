package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for managing {@link Permission} entities.
 *
 * <p>
 * Extends {@link JpaRepository} for CRUD operations,
 * {@link JpaSpecificationExecutor} for criteria-based queries,
 * and {@link RevisionRepository} to support auditing via Envers.
 * </p>
 *
 * <p>
 * Provides additional methods for retrieving permissions by status
 * and for filtering out soft-deleted permissions.
 * </p>
 *
 * @see Permission
 * @see Status
 * @see JpaRepository
 * @see JpaSpecificationExecutor
 * @see RevisionRepository
 */
@Repository
public interface PermissionRepository extends
        JpaRepository<Permission, Integer>,
        JpaSpecificationExecutor<Permission>,
        RevisionRepository<Permission, Integer, Integer> {

    /**
     * Retrieves all permissions with a status included in the given list.
     *
     * @param statusList the list of {@link Status} values to filter by
     * @return a list of matching {@link Permission} entities
     */
    List<Permission> findByStatusIn(List<Status> statusList);

    /**
     * Finds a permission by its ID, excluding the specified status (commonly {@link Status#DELETED}).
     *
     * @param id the ID of the permission
     * @param status the status to exclude
     * @return an {@link Optional} containing the {@link Permission}, if found and not matching the excluded status
     */
    Optional<Permission> findByIdAndStatusNot(Integer id, Status status);
}