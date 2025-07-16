package cv.igrp.platform.access_management.role.domain.service;

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
 * <p>This validator checks for conditions such as duplicate role names within a department.
 * Validation results are encapsulated in a {@link ResourceValidationResponse} object, which includes
 * status and detailed failure messages.
 */
public class RoleValidator {

    private static final Logger logger = LoggerFactory.getLogger(RoleValidator.class);

    /**
     * Validates the given {@link RoleDTO} based on business rules, such as duplicate role name within
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
        validateRoleName(roleDTO, department, response);
        return response;
    }

    /**
     * Validates whether the role name provided in {@link RoleDTO} already exists in the given
     * {@link DepartmentEntity}.
     *
     * @param roleDTO the role data to be validated
     * @param department the department in which the role is being created
     * @param response the validation response object to be updated with failure messages if any
     */
    private static void validateRoleName(RoleDTO roleDTO, DepartmentEntity department, ResourceValidationResponse response) {
        if(department != null && department.getRoles() != null){
            Optional<RoleEntity> optionalRoleSameName = department.getRoles()
                    .stream()
                    .filter(role -> role.getName().equalsIgnoreCase(roleDTO.getName()))
                    .findFirst();
            if(optionalRoleSameName.isPresent()){
                logger.warn("Role with name {} exists in Department {}.", roleDTO.getName(), department.getId());
                response.setValid(false);
                response.addFailureMessage("Role with name: " + roleDTO.getName() + " exists in Department: " + department.getId());
            }
        }
    }
}
