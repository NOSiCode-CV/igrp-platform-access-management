package cv.igrp.platform.access_management.shared.application.constants;

import lombok.Getter;

@Getter
public enum ResourceItemType {

  PAGE(
    "PAGE", 
    "Page"
  ),
    API_RESOURCE(
    "API_RESOURCE", 
    "API Resource"
  )
  ;

  private final String code;
  private final String description;

  ResourceItemType(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
