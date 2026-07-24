package cv.igrp.platform.access_management.department.domain.exceptions;

/**
 * Thrown when a non-superadmin caller attempts to act on a department that is
 * not in their management scope (their own departments plus transitive
 * descendants). Handled by GlobalExceptionHandler which returns 403 with an
 * error code the frontend can distinguish from a plain permission-denied.
 */
public class OutOfScopeException extends RuntimeException {

    private final Integer departmentId;

    public OutOfScopeException(Integer departmentId) {
        super("You cannot manage department " + departmentId + " — not in your scope.");
        this.departmentId = departmentId;
    }

    public Integer getDepartmentId() {
        return departmentId;
    }
}
