package cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.entity.GlobalConfigurationEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;


@Repository
public interface GlobalConfigurationEntityRepository extends
    JpaRepository<GlobalConfigurationEntity, Integer>,
    JpaSpecificationExecutor<GlobalConfigurationEntity>
{

    List<GlobalConfigurationEntity> findByTypeOrderByLastModifiedDateDesc(GlobalConfigurationType type);

}