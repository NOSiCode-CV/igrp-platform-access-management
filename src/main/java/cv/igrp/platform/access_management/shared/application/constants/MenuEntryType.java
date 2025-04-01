package cv.igrp.platform.access_management.shared.application.constants;

import lombok.Getter;

@Getter
public enum MenuEntryType {

  MENU_PAGE(
    "MENU_PAGE", 
    "Menu Page"
  ),
    EXTERNAL_PAGE(
    "EXTERNAL_PAGE", 
    "External Page"
  ),
    FOLDER(
    "FOLDER", 
    "Folder"
  )
  ;

  private final String code;
  private final String description;

  MenuEntryType(String code, String description) {
    this.code = code;
    this.description = description;
  }

}
