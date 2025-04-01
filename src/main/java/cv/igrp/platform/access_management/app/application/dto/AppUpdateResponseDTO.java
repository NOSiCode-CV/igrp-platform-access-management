package cv.igrp.platform.access_management.app.application.dto;

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
@Schema(description = "Class retrieved as response body after creating an application")
@IgrpDTO
public class AppUpdateResponseDTO {

      @Schema(
          name = "id",
          type = "String",
          description = "Application id"
      )
      private String id;
      @Schema(
          name = "type",
          type = "String",
          description = "App type. Possible values: INTERNAL and EXTERNAL"
      )
      private String type;
      @Schema(
          name = "owner",
          type = "String",
          description = "Application owner id"
      )
      private String owner;
      @Schema(
          name = "name",
          type = "String",
          description = "Application name"
      )
      private String name;
      @Schema(
          name = "description",
          type = "String",
          description = "Application description"
      )
      private String description;
      @Schema(
          name = "code",
          type = "String",
          description = "Application code, must be unique"
      )
      private String code;
      @Schema(
          name = "url",
          type = "String",
          description = "Application url, must be unique"
      )
      private String url;
      @Schema(
          name = "slug",
          type = "String",
          description = "Slug of the application"
      )
      private String slug;
      @Schema(
          name = "creationDate",
          type = "String",
          description = "Application creation date in ISO-8601 format"
      )
      private String creationDate;
      @Schema(
          name = "status",
          type = "String",
          description = "Application Status"
      )
      private String status;
      @Schema(
          name = "picture",
          type = "String",
          description = "Application picture url"
      )
      private String picture;

}
