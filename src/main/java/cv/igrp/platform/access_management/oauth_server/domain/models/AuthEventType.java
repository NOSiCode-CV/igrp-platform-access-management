package cv.igrp.platform.access_management.oauth_server.domain.models;

/**
 * Enumerates the categories of authentication/authorization events captured
 * by the OAuth2 authorization server audit pipeline.
 */
public enum AuthEventType {

    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGOUT,
    TOKEN_ISSUED,
    TOKEN_REFRESH,
    TOKEN_REVOKED,
    ACCESS_DENIED
}
