package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RoleEntityRepository extends
        JpaRepository<RoleEntity, Integer>,
        JpaSpecificationExecutor<RoleEntity>,
        RevisionRepository<RoleEntity, Integer, Integer> {

    /**
     * Finds a role by its code.
     *
     * @param codes the code list of the roles
     * @param department the department the roles belongs to
     * @return an {@link List} containing the {@link RoleEntity} objects, or empty if not found
     */
    List<RoleEntity> findAllByDepartmentAndCodeIn(DepartmentEntity department, List<String> codes);

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
     * @param department the department the role belongs to
     * @param id     the ID of the role
     * @param status the status to exclude (e.g., {@link Status#DELETED})
     * @return an {@link Optional} containing the {@link RoleEntity}, if found and not having the excluded status
     */
    Optional<RoleEntity> findByDepartmentAndIdAndStatusNot(DepartmentEntity department, Integer id, Status status);

    Optional<RoleEntity> findByDepartmentAndCodeAndStatusNot(DepartmentEntity department, String code, Status status);

    default RoleEntity findByDepartmentAndCodeAndStatusNotDeleted(DepartmentEntity department, String code) {
        return findByDepartmentAndCodeAndStatusNot(department, code, Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.notFound("Role with code: <" + code + "> was not found"));
    }

    @Query("""
        select r.id from RoleEntity r
        where r.code = :code and r.status <> 'DELETED'
    """)
    Integer findIdByCode(String code);

    @Query("""
        select child.id
        from RoleEntity child
        join child.parent parent
        where parent.id = :parentId and child.status <> 'DELETED'
    """)
    Set<Integer> findDirectChildren(Integer parentId);

    @Query(
    """
        select r from RoleEntity r
        JOIN r.users u
        where r.department = :department and u = :user and r.status <> 'DELETED'
    """
    )
    List<RoleEntity> findByDepartmentIdAndUserIdAndStatusNotDeleted(IGRPUserEntity user, DepartmentEntity department);

    List<RoleEntity> findAllByDepartmentAndStatusNot(DepartmentEntity  department, Status status);

    default List<RoleEntity> findAllByDepartmentAndStatusNotDeleted(DepartmentEntity department) {
        return findAllByDepartmentAndStatusNot(department, Status.DELETED);
    }
}