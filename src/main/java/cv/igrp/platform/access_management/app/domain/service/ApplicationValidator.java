package cv.igrp.platform.access_management.app.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;


/**
 * Validator class responsible for applying business rules related to {@link ApplicationDTO}.
 *
 * <p>This class currently validates application code uniqueness within a given {@link ApplicationEntity}.
 * It returns a {@link ResourceValidationResponse} indicating whether the validation passed or failed.
 *
 * <p>Designed to be extended with additional validation rules as needed.
 */
@Component
public class ApplicationValidator {

    private static Logger Log = LoggerFactory.getLogger(ApplicationValidator.class);

    private ApplicationEntityRepository applicationEntityRepository;

    public ApplicationValidator(ApplicationEntityRepository applicationEntityRepository) {
        this.applicationEntityRepository = applicationEntityRepository;
    }

    /**
     * Validates that the application name does not already exist within the given application.
     *
     * @param applicationDTO the application data to validate
     * @return a {@link ResourceValidationResponse} indicating the result of the validation
     */
    public ResourceValidationResponse validateApplicationCode(ApplicationDTO applicationDTO) {
        ResourceValidationResponse result = new ResourceValidationResponse();
        result.setValid(true);
        Optional<ApplicationEntity> app = applicationEntityRepository.findByCodeAndStatusNot(applicationDTO.getCode(), Status.DELETED);
        if (app.isPresent()) {
            result.setValid(false);
        }
        return result;
    }
}
