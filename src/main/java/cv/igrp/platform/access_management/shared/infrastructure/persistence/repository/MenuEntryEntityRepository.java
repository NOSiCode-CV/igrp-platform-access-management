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

    @Query("SELECT m FROM MenuEntryEntity m WHERE m.applicationId = :appId " +
            "AND (m.type = 'SYSTEM_PAGE' OR m.id IN " +
            "(SELECT DISTINCT p.parentId.id FROM MenuEntryEntity p WHERE p.type = 'SYSTEM_PAGE' AND p.applicationId = :appId))")
    List<MenuEntryEntity> findSystemMenuHierarchy(@Param("appId") ApplicationEntity appId);

    Optional<MenuEntryEntity> findByCode(String code);

    List<MenuEntryEntity> findByApplicationIdAndStatus(ApplicationEntity appId, Status status);

    Optional<MenuEntryEntity> findByCodeAndStatusNot(String code, Status status);
}