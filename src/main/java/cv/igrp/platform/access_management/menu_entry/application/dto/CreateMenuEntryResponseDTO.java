package cv.igrp.platform.access_management.menu_entry.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Class used specifically as response body when creating a menu entry")
@IgrpDTO
public class CreateMenuEntryResponseDTO {

      @Schema(
          name = "type",
          type = "MenuEntryType",
          implementation = MenuEntryType.class,
          description = "Menu Entry type. Values : FOLDER, MENU_PAGE or EXTERNAL_PAGE"
      )
      private MenuEntryType type;
      @Schema(
          name = "id",
          type = "Integer",
          description = "Menu Entry id"
      )
      private Integer id;
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

}
