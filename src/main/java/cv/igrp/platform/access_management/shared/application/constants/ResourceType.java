package cv.igrp.platform.access_management.shared.application.constants;

import lombok.Getter;

@Getter
public enum ResourceType {

  API(
    "API", 
    "API"
  ),
    UI(
    "UI", 
    "User Interface"
  )
  ;

  private final String code;
  private final String description;

  ResourceType(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
