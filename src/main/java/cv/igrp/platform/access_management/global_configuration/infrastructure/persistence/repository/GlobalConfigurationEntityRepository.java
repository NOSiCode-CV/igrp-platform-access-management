package cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.entity.GlobalConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GlobalConfigurationEntityRepository extends JpaRepository<GlobalConfigurationEntity, Integer> {

    /**
     * Find the configuration by type.
     * Note: Added to satisfy explicit user request.
     */
    Optional<GlobalConfigurationEntity> findByType(GlobalConfigurationType type);

    /**
     * Find configuration by type ordered by last modified date descending.
     * Note: Required by existing GetGlobalConfigurationQueryHandler logic.
     */
    List<GlobalConfigurationEntity> findByTypeOrderByLastModifiedDateDesc(GlobalConfigurationType type);
}
