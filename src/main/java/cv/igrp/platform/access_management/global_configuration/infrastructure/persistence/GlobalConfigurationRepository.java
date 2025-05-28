package cv.igrp.platform.access_management.global_configuration.infrastructure.persistence;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.domain.models.GlobalConfiguration;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;



@Repository
public interface GlobalConfigurationRepository extends
    JpaRepository<GlobalConfiguration, Integer>,
    JpaSpecificationExecutor<GlobalConfiguration>
{

    List<GlobalConfiguration> findByTypeOrderByLastModifiedDateDesc(GlobalConfigurationType type);

}