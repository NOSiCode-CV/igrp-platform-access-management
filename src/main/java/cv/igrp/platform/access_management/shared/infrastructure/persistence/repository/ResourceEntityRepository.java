package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ResourceEntityRepository extends
    JpaRepository<ResourceEntity, Integer>,
    JpaSpecificationExecutor<ResourceEntity>,
    RevisionRepository<ResourceEntity, Integer, Integer>
{

    Optional<ResourceEntity> findByNameAndStatusNot(String name, Status status);

}