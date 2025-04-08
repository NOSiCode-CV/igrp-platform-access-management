package cv.igrp.platform.access_management.resource.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import cv.igrp.platform.access_management.shared.application.constants.ResourceType;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Class retrieved as response body after creating an application resource")
@IgrpDTO
public class ResourceResponseDTO {

      @Schema(
          name = "id",
          type = "Integer",
          description = "Id of the resource created"
      )
      private Integer id;
      @Schema(
          name = "type",
          type = "ResourceType",
          implementation = ResourceType.class,
          description = "Type of the resource created"
      )
      private ResourceType type;
      @Schema(
          name = "url",
          type = "String",
          description = "Url of the resource created"
      )
      private String url;
      @Schema(
          name = "description",
          type = "String",
          description = "Description of the resource created"
      )
      private String description;
      @Schema(
          name = "creationDate",
          type = "String",
          description = "Resource creation date in ISO-8601 format"
      )
      private String creationDate;
      @Schema(
          name = "status",
          type = "String",
          description = "Resource Status"
      )
      private String status;
      @Schema(
          name = "resourceItemsIds",
          type = "Integer",
          description = "Resource Items associated with the resource"
      )
      private Integer resourceItemsIds;

}
