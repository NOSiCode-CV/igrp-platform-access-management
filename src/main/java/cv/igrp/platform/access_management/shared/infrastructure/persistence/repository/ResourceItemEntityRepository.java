package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface ResourceItemEntityRepository extends
    JpaRepository<ResourceItemEntity, Integer>,
    JpaSpecificationExecutor<ResourceItemEntity>,
    RevisionRepository<ResourceItemEntity, Integer, Integer>
{

    Optional<ResourceItemEntity> findByName(String name);

}