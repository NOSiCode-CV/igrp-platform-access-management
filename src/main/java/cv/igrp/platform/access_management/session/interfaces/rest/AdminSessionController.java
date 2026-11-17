package cv.igrp.platform.access_management.session.interfaces.rest;

import cv.igrp.framework.core.domain.CommandBus;
import cv.igrp.framework.stereotype.IgrpController;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import cv.igrp.platform.access_management.session.application.commands.KillAllUserSessionsCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionsByDepartmentCommand;
import cv.igrp.platform.access_management.session.application.commands.KillSessionsByRoleCommand;
import cv.igrp.platform.access_management.session.application.dto.SessionKillRequestDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * An administrator ending or refreshing users' access (spec "Logout, kill and refresh", #33).
 * There are no sessions: a kill revokes every authorization of the user and deletes all of
 * their permission keys, always on the whole platform; a refresh recomputes a user's key for
 * one application from their current roles.
 */
@Slf4j
@IgrpController
@RestController
@RequestMapping("/api/admin/sessions")
@Tag(name = "Admin Session Management", description = "Kill a user's access, or refresh their permissions")
public class AdminSessionController {

    private final CommandBus commandBus;
    private final PermissionFetchService permissionFetch;

    public AdminSessionController(CommandBus commandBus, PermissionFetchService permissionFetch) {
        this.commandBus = commandBus;
        this.permissionFetch = permissionFetch;
    }

    @PostMapping("/users/{userId}/kill")
    @Operation(summary = "Kill a user",
            description = "Ends the user's access on the whole platform: every authorization and permission key")
    @ApiResponse(responseCode = "204", description = "The user's access ended")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<Void> killUser(
            @Parameter(description = "User id") @PathVariable String userId,
            @Valid @RequestBody SessionKillRequestDTO request) {

        log.info("Admin killing user {} with reason: {}", userId, request.getReason());
        boolean killed = commandBus.send(new KillAllUserSessionsCommand(userId, request.getReason(),
                request.getKilledBy()));
        return killed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/roles/{roleCode}/kill")
    @Operation(summary = "Kill the users of a role",
            description = "Kills every user holding the role, each on the whole platform")
    @ApiResponse(responseCode = "204", description = "The users' access ended")
    @ApiResponse(responseCode = "404", description = "No user holds the role")
    public ResponseEntity<Void> killSessionsByRole(
            @Parameter(description = "Role code") @PathVariable String roleCode,
            @Parameter(description = "Department code (role codes are department-scoped)")
            @RequestParam(required = false) String departmentCode,
            @Valid @RequestBody SessionKillRequestDTO request) {

        log.info("Admin killing the users of role {} in department {} with reason: {}",
                roleCode, departmentCode, request.getReason());
        int killed = commandBus.send(new KillSessionsByRoleCommand(roleCode, departmentCode, request.getReason(),
                request.getKilledBy()));
        return killed == 0 ? ResponseEntity.notFound().build() : ResponseEntity.noContent().build();
    }

    @PostMapping("/departments/{departmentCode}/kill")
    @Operation(summary = "Kill the users of a department",
            description = "Kills every user with a role in the department, each on the whole platform")
    @ApiResponse(responseCode = "204", description = "The users' access ended")
    public ResponseEntity<Void> killSessionsByDepartment(
            @Parameter(description = "Department code") @PathVariable String departmentCode,
            @Valid @RequestBody SessionKillRequestDTO request) {

        log.info("Admin killing the users of department {} with reason: {}", departmentCode, request.getReason());
        commandBus.send(new KillSessionsByDepartmentCommand(departmentCode, request.getReason(),
                request.getKilledBy()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/refresh")
    @Operation(
            summary = "Refresh a user's permissions in one application",
            description = "Recomputes the user's permission key for the application from their current roles and "
                    + "assignments and writes it, exactly as the user's own permission fetch would (#19). "
                    + "Nothing is taken from the request: there is no body."
    )
    @ApiResponse(responseCode = "200", description = "The written permission set")
    @ApiResponse(responseCode = "404", description = "User or application not found")
    @ApiResponse(responseCode = "409", description = "The user is not active or not signed in: nothing is written")
    @ApiResponse(responseCode = "503", description = "Redis is unavailable: nothing is written")
    public ResponseEntity<PermissionSetDTO> refreshUserPermissions(
            @Parameter(description = "User id") @PathVariable String userId,
            @Parameter(description = "Application code") @RequestParam String applicationCode) {

        log.info("Admin refreshing the permissions of user {} in {}", userId, applicationCode);
        return ResponseEntity.ok(permissionFetch.refresh(userId, applicationCode));
    }
}
