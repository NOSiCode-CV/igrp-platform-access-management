package cv.igrp.platform.access_management.shared.application.constants;

import lombok.Getter;

@Getter
public enum AppType {

  EXTERNAL(
    "EXTERNAL", 
    "External"
  ),
    INTERNAL(
    "INTERNAL", 
    "Internal"
  )
  ;

  private final String code;
  private final String description;

  AppType(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
