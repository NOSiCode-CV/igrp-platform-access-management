package cv.igrp.platform.access_management.department.application.constants;

import lombok.Getter;

@Getter
public enum DepartmentStatus {

  ACTIVE(
    "ACTIVE", 
    "Active"
  ),
    PENDING(
    "PENDING", 
    "Pending"
  ),
    INACTIVE(
    "INACTIVE", 
    "Inactive"
  ),
    IN_PROGRESS(
    "IN_PROGRESS", 
    "In Progress"
  )
  ;

  private final String code;
  private final String description;

  DepartmentStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
