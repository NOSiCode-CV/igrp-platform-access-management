package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface MenuEntryEntityRepository extends
    JpaRepository<MenuEntryEntity, Integer>,
    JpaSpecificationExecutor<MenuEntryEntity>,
    RevisionRepository<MenuEntryEntity, Integer, Integer>
{

}