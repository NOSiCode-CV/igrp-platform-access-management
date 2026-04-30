package cv.igrp.platform.access_management.shared.domain.audit;

public enum AuthEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    TOKEN_ISSUED,
    TOKEN_INVALID,
    LOGOUT,
    IDENTITY_LINKED,
    IDENTITY_LINK_FAILED,
    SESSION_EXPIRED
}
