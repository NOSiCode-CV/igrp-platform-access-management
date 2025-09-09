package cv.igrp.platform.access_management.menu.application.domain.service;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * Utility class responsible for validating {@link MenuEntryDTO} instances against business rules.
 *
 * <p>This validator checks for conditions such as fields setting for a specific menu entry type.
 * Validation results are encapsulated in a {@link ResourceValidationResponse} object, which includes
 * status and detailed failure messages.
 */
public class MenuEntryValidator {

    private static final Logger logger = LoggerFactory.getLogger(MenuEntryValidator.class);

    public static void validateRequiredFields(MenuEntryDTO menuEntry) {

        if(menuEntry.getType().equals(MenuEntryType.SYSTEM_PAGE)) {
            if(menuEntry.getPageSlug() == null)
                throw IgrpResponseStatusException.of(
                        HttpStatus.BAD_REQUEST, "Page Slug Required", "Page Slug must be provided for system menu types"
                );
        }

        if(menuEntry.getType().equals(MenuEntryType.MENU_PAGE)) {

            if(menuEntry.getPageSlug() == null)
                throw IgrpResponseStatusException.of(
                        HttpStatus.BAD_REQUEST, "Page Slug Required", "Page Slug must be provided for system menu types"
                );

            if(menuEntry.getUrl() == null)
                throw IgrpResponseStatusException.of(
                        HttpStatus.BAD_REQUEST, "Page URL Required", "Page URL must be provided for system menu types"
                );

        }

        if(menuEntry.getType().equals(MenuEntryType.EXTERNAL_PAGE)) {

            if(menuEntry.getUrl() == null)
                throw IgrpResponseStatusException.of(
                        HttpStatus.BAD_REQUEST, "Page URL Required", "Page URL must be provided for system menu types"
                );

        }

    }

}
