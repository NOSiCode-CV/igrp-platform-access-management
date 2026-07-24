package cv.igrp.platform.access_management.department.domain.exceptions;

/**
 * Thrown when a non-superadmin attempts to create a root department
 * (parentId == null). Only superadmins may create root departments.
 */
public class RootDepartmentForbiddenException extends RuntimeException {

    public RootDepartmentForbiddenException() {
        super("Only superadmins can create root departments (parentId == null).");
    }
}
