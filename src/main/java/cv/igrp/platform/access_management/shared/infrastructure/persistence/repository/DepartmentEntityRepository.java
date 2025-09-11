package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface DepartmentEntityRepository extends
    JpaRepository<DepartmentEntity, Integer>,
    JpaSpecificationExecutor<DepartmentEntity>,
    RevisionRepository<DepartmentEntity, Integer, Integer>
{

    Optional<DepartmentEntity> findByCodeAndStatusNot(String code, DepartmentStatus status);

    boolean existsByCode(String code);

    void deleteByCode(String code);

}