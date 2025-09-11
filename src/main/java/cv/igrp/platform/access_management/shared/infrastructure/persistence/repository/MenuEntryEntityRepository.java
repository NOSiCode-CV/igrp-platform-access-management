package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface MenuEntryEntityRepository extends
    JpaRepository<MenuEntryEntity, Integer>,
    JpaSpecificationExecutor<MenuEntryEntity>,
    RevisionRepository<MenuEntryEntity, Integer, Integer>
{

    List<MenuEntryEntity> findByApplicationIdAndStatus(ApplicationEntity appId, Status status);

    Optional<MenuEntryEntity> findByCodeAndStatusNot(String code, Status status);
}