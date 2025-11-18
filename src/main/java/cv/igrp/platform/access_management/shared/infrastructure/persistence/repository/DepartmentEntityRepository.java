package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
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

}