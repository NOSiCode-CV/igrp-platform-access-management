package cv.igrp.platform.access_management.m2m.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationSyncService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationSyncService.class);

    private final ApplicationEntityRepository applicationRepository;

    public ApplicationSyncService(ApplicationEntityRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    /**
     * Synchronizes the application definition with the database.
     * <p>
     * - Creates new application if it doesn't exist.<br>
     * - Updates application if it differs.<br>
     * - Ignores fields not relevant for synchronization.<br>
     * </p>
     *
     * @param applicationDTO the application definition to synchronize
     */
    @Transactional
    public void synchronizeApplication(ApplicationDTO applicationDTO) {

        if (applicationDTO == null || applicationDTO.getCode() == null || applicationDTO.getCode().isBlank()) {
            throw IgrpResponseStatusException.badRequest("Application code is required");
        }

        LOGGER.info("[ApplicationSync] Starting synchronization for application '{}'", applicationDTO.getCode());

        try {

            ApplicationEntity existing = applicationRepository.findByCodeAndStatusNot(applicationDTO.getCode(), Status.DELETED).orElse(null);

            if (existing != null) {
                // Check for differences
                if (!existing.getName().equals(applicationDTO.getName()) ||
                        !existing.getDescription().equals(applicationDTO.getDescription()) ||
                        !existing.getStatus().equals(applicationDTO.getStatus()) ||
                        !existing.getType().equals(applicationDTO.getType())) {

                    existing.setName(applicationDTO.getName());
                    existing.setDescription(applicationDTO.getDescription());
                    existing.setStatus(applicationDTO.getStatus());
                    existing.setType(applicationDTO.getType());
                    applicationRepository.save(existing);
                    LOGGER.info("[ApplicationSync] Updated application '{}'", applicationDTO.getCode());
                } else {
                    LOGGER.info("[ApplicationSync] Application '{}' already up to date.", applicationDTO.getCode());
                }
            } else {
                // Create new
                ApplicationEntity newApp = new ApplicationEntity();
                newApp.setCode(applicationDTO.getCode());
                newApp.setName(applicationDTO.getName());
                newApp.setDescription(applicationDTO.getDescription());
                newApp.setStatus(applicationDTO.getStatus());
                newApp.setType(applicationDTO.getType());
                applicationRepository.save(newApp);
                LOGGER.info("[ApplicationSync] Created new application '{}'", applicationDTO.getCode());
            }

        } catch (Exception e) {
            LOGGER.error("[ApplicationSync] Failed to synchronize application '{}': {}", applicationDTO != null ? applicationDTO.getCode() : "null", e.getMessage());
            throw IgrpResponseStatusException.internalServerError(e.getMessage());
        }
    }
}