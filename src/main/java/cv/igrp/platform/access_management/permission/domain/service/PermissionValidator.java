package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * Validator class responsible for applying business rules related to {@link PermissionDTO}.
 *
 * <p>This class currently validates permission name uniqueness within a given {@link Application}.
 * It returns a {@link ResourceValidationResponse} indicating whether the validation passed or failed.
 *
 * <p>Designed to be extended with additional validation rules as needed.
 */
public class PermissionValidator {

    private static Logger Log = LoggerFactory.getLogger(PermissionValidator.class);


    /**
     * Validates that the permission name does not already exist within the given application.
     *
     * @param permissionDTO the permission data to validate
     * @param application the application to check against
     * @return a {@link ResourceValidationResponse} indicating the result of the validation
     */
    public static ResourceValidationResponse validatePermissionName(PermissionDTO permissionDTO, Application application) {
        ResourceValidationResponse result = new ResourceValidationResponse();
        result.setValid(true);
        if (application.getPermissions() != null) {
            Optional<Permission> optionalPermission = application.getPermissions().stream()
                    .filter(permission -> permission.getName().equalsIgnoreCase(permissionDTO.getName()))
                    .findFirst();

            if (optionalPermission.isPresent()) {
                Log.warn("Permission with name {} exists in Application {}.", permissionDTO.getName(), application.getId());
                result.setValid(false);
                result.addFailureMessage("Permission with name: " + permissionDTO.getName() + " exists in Application: " + application.getId());
            }
        }
        return result;
    }
}
