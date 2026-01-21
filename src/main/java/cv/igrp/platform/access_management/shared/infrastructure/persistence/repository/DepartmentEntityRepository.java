package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface DepartmentEntityRepository extends
        JpaRepository<DepartmentEntity, Integer>,
        JpaSpecificationExecutor<DepartmentEntity>,
        RevisionRepository<DepartmentEntity, Integer, Integer> {

    Optional<DepartmentEntity> findByCodeAndStatusNot(String code, DepartmentStatus status);

    default DepartmentEntity findByCodeAndStatusNotDeleted(String code) {
        return findByCodeAndStatusNot(code, DepartmentStatus.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.notFound(
                        "Department not found",
                        "No department found with code: " + code));
    }

    boolean existsByCode(String code);

    void deleteByCode(String code);

    @Query("""
        select d.id from DepartmentEntity d
        where d.code = :code and d.status <> 'DELETED'
    """)
    Integer findIdByCode(String code);

    @Query("""
        select child.id
        from DepartmentEntity child
        join child.parentId parent
        where parent.id = :parentId and child.status <> 'DELETED'
    """)
    Set<Integer> findDirectChildren(Integer parentId);

    @Query("""
        select d
        from DepartmentEntity d
        where d.id in :ids
    """)
    List<DepartmentEntity> findByIds(Set<Integer> ids);

    @Query(
            """
            select d
            from DepartmentEntity d
            join d.roles r
            join r.users u
            where u = :user and d.status <> 'DELETED'
        """
    )
    List<DepartmentEntity> findByUserIdAndStatusNotDeleted(IGRPUserEntity user);

    List<DepartmentEntity> findByStatus(DepartmentStatus status);

    default List<DepartmentEntity> findAllAndStatusActive() {
        return findByStatus(DepartmentStatus.ACTIVE);
    }

    @Query(value = """
    SELECT d.*
    FROM t_department d
    WHERE d.status = 'ACTIVE'
      AND (:code IS NULL OR d.code ILIKE CONCAT('%', :code, '%'))
""", nativeQuery = true)
    List<DepartmentEntity> findAllActiveFiltered(
            @Param("code") String departmentCode
    );

    @Query(value = """
    SELECT DISTINCT d.*
    FROM t_department d
    JOIN t_role r ON r.department = d.id
    JOIN t_role_users ru ON ru.roles_id = r.id
    JOIN t_user u ON u.id = ru.users_id
    WHERE u.id = :userId
      AND d.status <> 'DELETED'
      AND (:code IS NULL OR d.name ILIKE CONCAT('%', :code, '%'))
""", nativeQuery = true)
    List<DepartmentEntity> findByUserAndNotDeletedFiltered(Integer userId, @Param("code") String departmentCode);


}