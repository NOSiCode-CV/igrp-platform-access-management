package cv.igrp.platform.access_management.resource.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;



@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Class retrieved as response body after creating a resource item")
@IgrpDTO
public class ResourceItemResponseDTO {

      @Schema(
          name = "id",
          type = "Integer",
          description = "ID of the resource item"
      )
      private Integer id;
      @Schema(
          name = "name",
          type = "String",
          description = "Name of the resource item"
      )
      private String name;
      @Schema(
          name = "url",
          type = "String",
          description = "Url of the resource item"
      )
      private String url;
      @Schema(
          name = "description",
          type = "String",
          description = "Description of the resource item"
      )
      private String description;
      @Schema(
          name = "resourceId",
          type = "Integer",
          description = "Resource associated with the resource item&#x27;s ID"
      )
      private Integer resourceId;

}
