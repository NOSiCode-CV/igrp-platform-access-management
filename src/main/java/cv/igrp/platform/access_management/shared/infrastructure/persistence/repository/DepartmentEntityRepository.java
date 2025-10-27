package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

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
}