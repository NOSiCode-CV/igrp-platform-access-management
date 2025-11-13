package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleEntityRepository extends
        JpaRepository<RoleEntity, Integer>,
        JpaSpecificationExecutor<RoleEntity>,
        RevisionRepository<RoleEntity, Integer, Integer> {

    /**
     * Finds a role by its name.
     *
     * @param codes the code list of the roles
     * @return an {@link Optional} containing the {@link RoleEntity} if found, or empty if not found
     */
    List<RoleEntity> findAllByCodeIn(List<String> codes);

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

    Optional<RoleEntity> findByCodeAndStatusNot(String code, Status status);

    default RoleEntity findByCodeAndStatusNotDeleted(String code) {
        return findByCodeAndStatusNot(code, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.notFound("Role not found with code: %s".formatted(code)));
    }

}