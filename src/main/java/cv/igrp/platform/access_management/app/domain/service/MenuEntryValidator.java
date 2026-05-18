package cv.igrp.platform.access_management.app.domain.service;

import cv.igrp.platform.access_management.app.application.commands.CreateMenuCommand;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;


/**
 * Validator class responsible for applying business rules related to {@link MenuEntryDTO}.
 *
 * <p>This class currently validates menu entry code uniqueness within a given {@link MenuEntryEntity}.
 * It returns a {@link ResourceValidationResponse} indicating whether the validation passed or failed.
 *
 * <p>Designed to be extended with additional validation rules as needed.
 */
@Component
public class MenuEntryValidator {

    private static Logger Log = LoggerFactory.getLogger(MenuEntryValidator.class);

    private MenuEntryEntityRepository menuEntryEntityRepository;
    private ApplicationEntityRepository applicationEntityRepository;

    public MenuEntryValidator(MenuEntryEntityRepository menuEntryEntityRepository, ApplicationEntityRepository applicationEntityRepository) {
        this.menuEntryEntityRepository = menuEntryEntityRepository;
        this.applicationEntityRepository = applicationEntityRepository;
    }

    /**
     * Validates that the menu entry code does not already exist within the given menu entry.
     *
     * @param command the menu entry data to validate
     * @return a {@link ResourceValidationResponse} indicating the result of the validation
     */
    public ResourceValidationResponse validateMenuEntryCode(CreateMenuCommand command) {
        MenuEntryDTO menuEntryDTO = command.getMenuentrydto();

        ResourceValidationResponse result = new ResourceValidationResponse();
        result.setValid(true);
        ApplicationEntity app = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getApplicationCode());

        Optional<MenuEntryEntity> menuEntry = menuEntryEntityRepository.findByApplicationIdAndCodeAndStatusNot(app, menuEntryDTO.getCode(), Status.DELETED);
        if (menuEntry.isPresent()) {
            result.setValid(false);
        }

        return result;
    }

    public static void validateRequiredFields(MenuEntryDTO menuEntry) {

        if(menuEntry.getType().equals(MenuEntryType.SYSTEM_PAGE)) {
            if(menuEntry.getPageSlug() == null)
                throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_MENU_PAGE_SLUG_REQUIRED_FOR_SYSTEM);
        }

        if(menuEntry.getType().equals(MenuEntryType.MENU_PAGE)) {

            if(menuEntry.getPageSlug() == null)
                throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_MENU_PAGE_SLUG_REQUIRED_FOR_PAGE);

        }

        if(menuEntry.getType().equals(MenuEntryType.EXTERNAL_PAGE)) {

            if(menuEntry.getUrl() == null)
                throw IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_MENU_URL_REQUIRED_FOR_EXTERNAL);

        }

    }

}
