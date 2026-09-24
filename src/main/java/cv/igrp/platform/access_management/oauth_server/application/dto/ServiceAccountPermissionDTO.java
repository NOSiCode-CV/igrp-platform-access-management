package cv.igrp.platform.access_management.oauth_server.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One permission granted directly to a service account, as an id/name pair.
 *
 * <p>Exists because {@code permissionIds} and {@code permissionNames} on
 * {@link ServiceAccountDTO} are two independently-built sets whose iteration
 * orders are uncorrelated — they cannot be zipped by position. Callers need the
 * id to send back in {@code permissionIds} and the name to display.
 *
 * <p>No department is carried: direct grants are not department-scoped, a
 * service account may hold any permission in the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAccountPermissionDTO {

    private Integer id;
    private String name;
}
