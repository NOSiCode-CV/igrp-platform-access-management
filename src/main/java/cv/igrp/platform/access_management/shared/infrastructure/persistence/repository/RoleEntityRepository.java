package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface RoleEntityRepository extends
    JpaRepository<RoleEntity, Integer>,
    JpaSpecificationExecutor<RoleEntity>,
    RevisionRepository<RoleEntity, Integer, Integer>
{

    /**
     * Finds a role by its name.
     *
     * @param name the name of the role
     * @return an {@link Optional} containing the {@link RoleEntity} if found, or empty if not found
     */
    List<RoleEntity> findAllByNameIn(List<String> name);

    /**
     * Retrieves all roles whose status is included in the given list.
     *
     * @param statuses the list of {@link Status} values to filter by
     * @return a list of roles matching the provided statuses
     */
    List<RoleEntity> findByStatusIn(List<Status> statuses);

    /**
     * Finds all child roles associated with a given parent role.
     *
     * @param parent the parent {@link RoleEntity}
     * @return a list of child roles having the given role as parent
     */
    List<RoleEntity> findByParent(RoleEntity parent);

    /**
     * Finds a role by ID, excluding the one with the given status (commonly used to exclude deleted roles).
     *
     * @param id     the ID of the role
     * @param status the status to exclude (e.g., {@link Status#DELETED})
     * @return an {@link Optional} containing the {@link RoleEntity}, if found and not having the excluded status
     */
    Optional<RoleEntity> findByIdAndStatusNot(Integer id, Status status);
    Optional<RoleEntity> findByNameAndStatusNot(String name, Status status);

}