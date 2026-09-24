package cv.igrp.platform.access_management.oauth_server.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One role held by a service account, as an id/code/department triple.
 *
 * <p>Exists because {@code roleIds} and {@code roleCodes} on
 * {@link ServiceAccountDTO} are two independently-built sets whose iteration
 * orders are uncorrelated — they cannot be zipped by position.
 *
 * <p>{@code departmentCode} is part of the identity, not decoration: role codes
 * are unique only within a department, so two distinct roles can share a code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccountRoleDTO {

    private Integer id;
    private String code;
    /** Department the role belongs to; null for a role with no department. */
    private String departmentCode;
}
