package cv.igrp.platform.access_management.permission.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * Validator class responsible for applying business rules related to {@link PermissionDTO}.
 *
 * <p>This class currently validates permission name uniqueness within a given {@link DepartmentEntity}.
 * It returns a {@link ResourceValidationResponse} indicating whether the validation passed or failed.
 *
 * <p>Designed to be extended with additional validation rules as needed.
 */
public class PermissionValidator {

    private static Logger Log = LoggerFactory.getLogger(PermissionValidator.class);


    /**
     * Validates that the permission name does not already exist within the given department.
     *
     * @param permissionDTO the permission data to validate
     * @param department the department to check against
     * @return a {@link ResourceValidationResponse} indicating the result of the validation
     */
    public static ResourceValidationResponse validatePermissionName(PermissionDTO permissionDTO, DepartmentEntity department) {
        ResourceValidationResponse result = new ResourceValidationResponse();
        result.setValid(true);
        if (department.getPermissions() != null) {
            Optional<PermissionEntity> optionalPermission = department.getPermissions().stream()
                    .filter(permission -> !permission.getStatus().equals(Status.DELETED) && permission.getName().equalsIgnoreCase(permissionDTO.getName()))
                    .findFirst();

            if (optionalPermission.isPresent()) {
                Log.warn("Permission with name {} exists in Department {}.", permissionDTO.getName(), department.getId());
                result.setValid(false);
                result.addFailureMessage("Permission with name: " + permissionDTO.getName() + " exists in Department: " + department.getId());
            }
        }
        return result;
    }

    /**
     * Normalizes a permission name to the format: departmentCode.name
     * - If the given name already starts with departmentCode + ".", it is returned as-is.
     * - Otherwise, the departmentCode is prepended to the name, separated by a dot.
     *
     * @param name the original permission name
     * @param departmentCode the department code
     * @return the normalized permission name
     */
    public static String normalizePermissionName(String name, String departmentCode) {
        if (name == null || departmentCode == null) {
            throw new IllegalArgumentException("Name and departmentCode must not be null");
        }

        String prefix = departmentCode + ".";
        if (name.startsWith(prefix)) {
            return name;
        }
        return prefix + name;
    }

}
