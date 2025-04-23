package cv.igrp.platform.access_management.shared.infrastructure.persistence;

import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;



@Repository
public interface CustomFieldRepository extends
    JpaRepository<CustomField, Integer>,
    JpaSpecificationExecutor<CustomField>
{
    Optional<CustomField> findByTableNameAndRecordId(String tableName, Integer recordId);
}