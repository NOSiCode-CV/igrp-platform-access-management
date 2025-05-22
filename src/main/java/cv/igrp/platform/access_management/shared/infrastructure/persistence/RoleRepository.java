package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;


/**
 * Repository interface for managing {@link Role} entities.
 *
 * <p>
 * Extends {@link JpaRepository} to provide basic CRUD operations and
 * {@link JpaSpecificationExecutor} to support specification-based queries.
 * </p>
 *
 * <p>
 * Includes additional methods for querying roles by status, parent relationship,
 * and excluding soft-deleted records.
 * </p>
 *
 * @see Role
 * @see Status
 * @see JpaRepository
 * @see JpaSpecificationExecutor
 */
@Repository
public interface RoleRepository extends
        JpaRepository<Role, Integer>,
        JpaSpecificationExecutor<Role> {

    /**
     * Finds a role by its ID.
     *
     * @param id the ID of the role
     * @return an {@link Optional} containing the {@link Role}, if found
     */
    Optional<Role> findById(Integer id);

    /**
     * Retrieves all roles whose status is included in the given list.
     *
     * @param statuses the list of {@link Status} values to filter by
     * @return a list of roles matching the provided statuses
     */
    List<Role> findByStatusIn(List<Status> statuses);

    /**
     * Finds all child roles associated with a given parent role.
     *
     * @param parent the parent {@link Role}
     * @return a list of child roles having the given role as parent
     */
    List<Role> findByParent(Role parent);

    /**
     * Finds a role by ID, excluding the one with the given status (commonly used to exclude deleted roles).
     *
     * @param id     the ID of the role
     * @param status the status to exclude (e.g., {@link Status#DELETED})
     * @return an {@link Optional} containing the {@link Role}, if found and not having the excluded status
     */
    Optional<Role> findByIdAndStatusNot(Integer id, Status status);

}