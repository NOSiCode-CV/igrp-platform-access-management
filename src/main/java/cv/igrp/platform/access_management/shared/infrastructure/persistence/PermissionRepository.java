package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends
        JpaRepository<Permission, Integer>,
        JpaSpecificationExecutor<Permission>,
        RevisionRepository<Permission, Integer, Integer> {

    List<Permission> findByStatusIn(List<Status> statusList);

    Optional<Permission> findByIdAndStatusNot(Integer id, Status status);
}