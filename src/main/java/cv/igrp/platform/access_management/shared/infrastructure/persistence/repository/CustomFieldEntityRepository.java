package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;



@Repository
public interface CustomFieldEntityRepository extends
    JpaRepository<CustomFieldEntity, Integer>,
    JpaSpecificationExecutor<CustomFieldEntity>
{
    Optional<CustomFieldEntity> findByTableNameAndRecordId(String tableName, Integer recordId);
}