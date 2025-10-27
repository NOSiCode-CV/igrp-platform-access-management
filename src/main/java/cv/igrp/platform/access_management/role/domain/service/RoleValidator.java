package cv.igrp.platform.access_management.role.domain.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Optional;


/**
 * Utility class responsible for validating {@link RoleDTO} instances against business rules.
 *
 * <p>This validator checks for conditions such as duplicate role codes within a department.
 * Validation results are encapsulated in a {@link ResourceValidationResponse} object, which includes
 * status and detailed failure messages.
 */
public class RoleValidator {

    private static final Logger logger = LoggerFactory.getLogger(RoleValidator.class);

    /**
     * Validates the given {@link RoleDTO} based on business rules, such as duplicate role code within
     * the same {@link DepartmentEntity}.
     *
     * @param roleDTO the role data to be validated
     * @param department the department in which the role is being created
     * @return a {@link ResourceValidationResponse} containing validation status and failure messages
     */
    public static ResourceValidationResponse validateRoleDto(RoleDTO roleDTO, DepartmentEntity department){
        ResourceValidationResponse response = new ResourceValidationResponse();
        response.setValid(true);
        response.setFailureMessage(new ArrayList<>());
        validateRoleCode(roleDTO, department, response);
        return response;
    }

    /**
     * Validates whether the role code provided in {@link RoleDTO} already exists in the given
     * {@link DepartmentEntity}.
     *
     * @param roleDTO the role data to be validated
     * @param department the department in which the role is being created
     * @param response the validation response object to be updated with failure messages if any
     */
    private static void validateRoleCode(RoleDTO roleDTO, DepartmentEntity department, ResourceValidationResponse response) {
        if(department != null && department.getRoles() != null){
            Optional<RoleEntity> optionalRoleSameCode = department.getRoles()
                    .stream()
                    .filter(role -> !role.getStatus().equals(Status.DELETED) && role.getCode().equalsIgnoreCase(roleDTO.getCode()))
                    .findFirst();
            if(optionalRoleSameCode.isPresent()){
                logger.warn("Role with code {} exists in Department {}.", roleDTO.getCode(), department.getId());
                response.setValid(false);
                response.addFailureMessage("Role with code: " + roleDTO.getCode() + " exists in Department: " + department.getId());
            }
        }
    }

    /**
     * Normalizes a role code to the format: departmentCode.code
     * - If the given code already starts with departmentCode + ".", it is returned as-is.
     * - Otherwise, the departmentCode is prepended to the code, separated by a dot.
     *
     * @param code the original role code
     * @param departmentCode the department code
     * @return the normalized role code
     */
    public static String normalizeRoleCode(String code, String departmentCode) {
        if (code == null || departmentCode == null) {
            throw new IllegalArgumentException("Code and departmentCode must not be null");
        }

        String prefix = departmentCode + ".";
        if (code.startsWith(prefix)) {
            return code;
        }
        return prefix + code;
    }
    
}
