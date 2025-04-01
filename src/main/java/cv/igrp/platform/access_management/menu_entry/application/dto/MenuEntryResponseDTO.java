package cv.igrp.platform.access_management.menu_entry.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import cv.igrp.platform.access_management.undefined.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.undefined.application.dto.FolderDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Class used to map menu entry information about a menu entry")
@IgrpDTO
public class MenuEntryResponseDTO {

      @Schema(
          name = "type",
          type = "MenuEntryType",
          implementation = MenuEntryType.class,
          description = "Menu Entry type. Values: FOLDER, MENU_PAGE or EXTERNAL_PAGE"
      )
      private MenuEntryType type;
      @Schema(
          name = "id",
          type = "Integer",
          description = "Menu Entry id"
      )
      private Integer id;
      @Schema(
          name = "icon",
          type = "String",
          description = "Menu Entry icon"
      )
      private String icon;
      @Schema(
          name = "name",
          type = "String",
          description = "Menu Entry name"
      )
      private String name;
      @Schema(
          name = "url",
          type = "String",
          description = "Menu Entry url, present if menu entry type is MENU_PAGE or EXTERNAL_PAGE"
      )
      private String url;
      @Schema(
          name = "target",
          type = "String",
          description = "Menu Entry target, present if menu entry type is MENU_PAGE or EXTERNAL_PAGE"
      )
      private String target;
      @Schema(
          name = "folderRef",
          type = "FolderDTO",
          implementation = FolderDTO.class,
          description = "Class that brings parent folder information"
      )
      private FolderDTO folderRef;
      @Schema(
          name = "userPermissions",
          type = "String",
          description = "List of the user permissions information"
      )
      private String userPermissions;

}
