package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface IGRPUserEntityRepository extends
    JpaRepository<IGRPUserEntity, Integer>,
    JpaSpecificationExecutor<IGRPUserEntity>,
    RevisionRepository<IGRPUserEntity, Integer, Integer>
{

    Optional<IGRPUserEntity> findByUsername(String username);

}