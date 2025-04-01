package cv.igrp.platform.access_management.shared.application.constants;

import lombok.Getter;

@Getter
public enum Status {

  ACTIVE(
    "ACTIVE", 
    "Active"
  ),
    INACTIVE(
    "INACTIVE", 
    "Inactive"
  ),
    DELETED(
    "DELETED", 
    "Deleted"
  )
  ;

  private final String code;
  private final String description;

  Status(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
