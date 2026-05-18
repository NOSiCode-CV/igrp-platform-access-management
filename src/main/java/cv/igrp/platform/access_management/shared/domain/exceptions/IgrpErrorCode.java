package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.HttpStatus;

import java.text.MessageFormat;

/**
 * Centralized catalog of error codes returned by the Access Management API.
 *
 * <p>Each constant binds:
 * <ul>
 *   <li>a stable, descriptive code (the enum name, e.g. {@code IGRP_AUTH_USER_NOT_FOUND})
 *       — exposed in the {@code code} property of the {@code ProblemDetail} response body
 *       and on {@link IgrpResponseStatusException#getCode()};</li>
 *   <li>the HTTP status that should be returned for that error;</li>
 *   <li>a descriptive English message template, in {@link MessageFormat} syntax,
 *       e.g. {@code "The token for the invitation {0} is expired"}.</li>
 * </ul>
 */
public enum IgrpErrorCode {

    // ─── User ────────────────────────────────────────────────────────────────
    IGRP_AUTH_USER_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "User not found with id: {0}"),
    IGRP_AUTH_USER_NOT_FOUND_BY_EXTERNAL_ID(HttpStatus.UNAUTHORIZED,
            "User with external id {0} not found"),
    IGRP_AUTH_USER_NOT_FOUND_BY_USERNAME(HttpStatus.NOT_FOUND,
            "User not found with username: {0}"),
    IGRP_AUTH_USER_INACTIVE(HttpStatus.FORBIDDEN,
            "User with external id {0} is inactive and cannot perform this action"),

    // ─── Application ─────────────────────────────────────────────────────────
    IGRP_AUTH_APPLICATION_NOT_FOUND_BY_CODE(HttpStatus.NOT_FOUND,
            "Application not found with code: {0}"),
    IGRP_AUTH_APPLICATION_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Application not found with id: {0}"),
    IGRP_AUTH_APPLICATION_CODE_REQUIRED(HttpStatus.BAD_REQUEST,
            "Application code is required"),
    IGRP_AUTH_APPLICATION_VALIDATION_FAILED(HttpStatus.CONFLICT,
            "Application validation failed: {0}"),
    IGRP_AUTH_APPLICATION_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to retrieve the created application"),
    IGRP_AUTH_APPLICATION_SYNC_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to synchronize application: {0}"),

    // ─── Department ──────────────────────────────────────────────────────────
    IGRP_AUTH_DEPARTMENT_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Department not found with id: {0}"),
    IGRP_AUTH_DEPARTMENT_NOT_FOUND_BY_CODE(HttpStatus.NOT_FOUND,
            "Department not found with code: {0}"),
    IGRP_AUTH_DEPARTMENT_INACTIVE(HttpStatus.BAD_REQUEST,
            "The department {0} is inactive"),
    IGRP_AUTH_DEPARTMENT_INVALID_STATUS(HttpStatus.BAD_REQUEST,
            "Invalid department status: {0}"),
    IGRP_AUTH_DEPARTMENT_CODE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST,
            "Department with code {0} already exists"),
    IGRP_AUTH_DEPARTMENT_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Parent department not found with code: {0}"),
    IGRP_AUTH_DEPARTMENT_ACCESS_DENIED(HttpStatus.BAD_REQUEST,
            "Department access denied: {0}"),
    IGRP_AUTH_DEPARTMENT_PARENT_APPLICATION_NOT_ASSIGNED(HttpStatus.FORBIDDEN,
            "Cannot associate department ''{0}'' because its parent department ''{1}'' is not assigned to the application ''{2}''"),

    // ─── Role ────────────────────────────────────────────────────────────────
    IGRP_AUTH_ROLE_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Role not found with id: {0}"),
    IGRP_AUTH_ROLE_NOT_FOUND_BY_CODE(HttpStatus.NOT_FOUND,
            "Role not found with code: {0}"),
    IGRP_AUTH_ROLE_INACTIVE(HttpStatus.BAD_REQUEST,
            "The role for user with sub {0} is inactive"),
    IGRP_AUTH_ROLES_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Roles not found: {0}"),
    IGRP_AUTH_PARENT_ROLE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Parent role not found with code: {0}"),
    IGRP_AUTH_CHILD_ROLE_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Child role not found with code: {0}"),
    IGRP_AUTH_ROLE_VALIDATION_FAILED(HttpStatus.CONFLICT,
            "Role validation failed: {0}"),

    // ─── Menu ────────────────────────────────────────────────────────────────
    IGRP_AUTH_MENU_NOT_FOUND_BY_CODE(HttpStatus.NOT_FOUND,
            "Menu not found with code: {0}"),
    IGRP_AUTH_MENU_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Menu entry not found with code: {0}"),
    IGRP_AUTH_MENU_ENTRY_NOT_FOUND_FOR_APPLICATION(HttpStatus.NOT_FOUND,
            "Menu entry with code ''{0}'' was not found for application with code ''{1}''"),
    IGRP_AUTH_MENU_ENTRY_DTO_MISSING(HttpStatus.BAD_REQUEST,
            "Menu entry DTO is missing"),
    IGRP_AUTH_MENU_PAGE_SLUG_REQUIRED_FOR_SYSTEM(HttpStatus.BAD_REQUEST,
            "Page slug must be provided for system menu types"),
    IGRP_AUTH_MENU_PAGE_SLUG_REQUIRED_FOR_PAGE(HttpStatus.BAD_REQUEST,
            "Page slug must be provided for menu page types"),
    IGRP_AUTH_MENU_URL_REQUIRED_FOR_EXTERNAL(HttpStatus.BAD_REQUEST,
            "Page URL must be provided for external menu types"),
    IGRP_AUTH_MENU_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Parent menu not found with code: {0}"),
    IGRP_AUTH_MENU_VALIDATION_FAILED(HttpStatus.CONFLICT,
            "Menu validation failed: {0}"),
    IGRP_AUTH_MENU_NOT_ASSOCIATED_TO_PARENT_DEPARTMENT(HttpStatus.BAD_REQUEST,
            "Cannot add menu ''{0}'' to department ''{1}'' because its parent department ''{2}'' is not associated with the menu"),

    // ─── Permission ──────────────────────────────────────────────────────────
    IGRP_AUTH_PERMISSION_NOT_FOUND_BY_NAME(HttpStatus.BAD_REQUEST,
            "Permission not found with name: {0}"),
    IGRP_AUTH_PERMISSIONS_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Permissions not found: {0}"),
    IGRP_AUTH_PERMISSION_VALID_LIST_EMPTY(HttpStatus.BAD_REQUEST,
            "No valid permissions were provided"),

    // ─── Resource ────────────────────────────────────────────────────────────
    IGRP_AUTH_RESOURCE_NOT_FOUND_BY_NAME(HttpStatus.NOT_FOUND,
            "Resource not found with name: {0}"),

    // ─── Invitation / OTP ───────────────────────────────────────────────────
    IGRP_AUTH_INVITATION_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Invitation not found with id: {0}"),
    IGRP_AUTH_INVITATION_NOT_FOUND_BY_TOKEN(HttpStatus.NOT_FOUND,
            "Invitation not found for token: {0}"),
    IGRP_AUTH_INVITATION_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST,
            "The token for the invitation {0} is expired"),
    IGRP_AUTH_INVITATION_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST,
            "Email identifier value is required"),
    IGRP_AUTH_INVITATION_EMAIL_MISMATCH(HttpStatus.BAD_REQUEST,
            "The provided email does not match the invitation"),
    IGRP_AUTH_INVITATION_STATUS_INVALID(HttpStatus.BAD_REQUEST,
            "Invalid invitation status for code: {0}"),
    IGRP_AUTH_INVITATION_RESPONSE_UNAUTHORIZED(HttpStatus.UNAUTHORIZED,
            "User is not authorized to respond to this invitation"),
    IGRP_AUTH_INVITATION_OTP_NOT_FOUND(HttpStatus.NOT_FOUND,
            "No pending OTP request found for this token"),
    IGRP_AUTH_INVITATION_OTP_EXPIRED(HttpStatus.BAD_REQUEST,
            "The OTP code has expired. Please request a new one"),
    IGRP_AUTH_INVITATION_OTP_INVALID(HttpStatus.BAD_REQUEST,
            "Invalid OTP code"),
    IGRP_AUTH_INVITATION_OTP_NOT_VALIDATED(HttpStatus.BAD_REQUEST,
            "The OTP code has not been validated. Please validate your OTP code before accepting the invitation"),
    IGRP_AUTH_INVITATION_OTP_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
            "Failed to send OTP email"),

    // ─── Favorite Application ────────────────────────────────────────────────
    IGRP_AUTH_FAVORITE_APPLICATION_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Favorite application not found with id: {0}"),
    IGRP_AUTH_FAVORITE_APPLICATION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST,
            "Application ''{0}'' is already a favorite of user ''{1}''"),
    IGRP_AUTH_FAVORITE_APPLICATION_NOT_FAVORITED(HttpStatus.BAD_REQUEST,
            "Application ''{0}'' was never a favorite of user ''{1}''"),
    IGRP_AUTH_FAVORITE_APPLICATION_NONE_FOR_USER(HttpStatus.NOT_FOUND,
            "No favorite applications found for user: {0}"),

    // ─── Access History ──────────────────────────────────────────────────────
    IGRP_AUTH_ACCESS_HISTORY_NOT_FOUND_BY_ID(HttpStatus.NOT_FOUND,
            "Access history not found with id: {0}"),

    // ─── Custom Fields ───────────────────────────────────────────────────────
    IGRP_AUTH_CUSTOM_FIELD_NOT_FOUND_FOR_APPLICATION(HttpStatus.NOT_FOUND,
            "Custom field not found for application with id: {0}"),

    // ─── Global Configuration ────────────────────────────────────────────────
    IGRP_AUTH_GLOBAL_CONFIGURATION_TYPE_NOT_FOUND(HttpStatus.BAD_REQUEST,
            "Global configuration type {0} is not supported"),
    IGRP_AUTH_GLOBAL_CONFIGURATION_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Global configuration not found for type: {0}"),

    // ─── File ────────────────────────────────────────────────────────────────
    IGRP_AUTH_FILE_REQUIRED(HttpStatus.BAD_REQUEST,
            "File is required and cannot be empty"),
    IGRP_AUTH_FILE_PATH_REQUIRED(HttpStatus.BAD_REQUEST,
            "File path is required and cannot be blank"),
    IGRP_AUTH_FILE_PRIVATE_URL_GENERATION_FAILED(HttpStatus.BAD_REQUEST,
            "Failed to generate private file URL for path: {0}"),
    IGRP_AUTH_FILE_UPLOAD_FAILED(HttpStatus.BAD_REQUEST,
            "Failed to upload file: {0}"),

    // ─── Session ─────────────────────────────────────────────────────────────
    IGRP_AUTH_SESSION_USER_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Session user not found with external id: {0}"),
    IGRP_AUTH_SESSION_USER_INACTIVE(HttpStatus.FORBIDDEN,
            "Inactive user cannot create a session: {0}"),

    // ─── M2M ─────────────────────────────────────────────────────────────────
    IGRP_AUTH_M2M_FILTER_REQUIRED(HttpStatus.BAD_REQUEST,
            "At least one filter must be provided: applicationCode, departmentCode, roleCode or permissionName"),

    // ─── Configuration / Infrastructure ──────────────────────────────────────
    IGRP_AUTH_APP_CENTER_URL_REQUIRED(HttpStatus.BAD_REQUEST,
            "Application Center URL is not configured"),

    // ─── Generic ─────────────────────────────────────────────────────────────
    IGRP_AUTH_INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR,
            "An internal server error occurred: {0}"),
    IGRP_AUTH_BAD_REQUEST(HttpStatus.BAD_REQUEST,
            "Bad request: {0}"),
    IGRP_AUTH_FORBIDDEN(HttpStatus.FORBIDDEN,
            "Forbidden: {0}"),
    IGRP_AUTH_NOT_FOUND(HttpStatus.NOT_FOUND,
            "Resource not found: {0}");

    private final HttpStatus status;
    private final String messageTemplate;

    IgrpErrorCode(HttpStatus status, String messageTemplate) {
        this.status = status;
        this.messageTemplate = messageTemplate;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    /** Formats the message template with the supplied arguments using {@link MessageFormat}. */
    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return messageTemplate;
        }
        return MessageFormat.format(messageTemplate, args);
    }
}
