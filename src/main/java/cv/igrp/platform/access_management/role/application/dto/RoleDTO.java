package cv.igrp.platform.access_management.role.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.Status;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class RoleDTO {

  @NotBlank(message = "The field <name> is required.")
	@Size(max = 15, message = "The field length <name> cannot be more than 15 characters.")
  private String name;
  @Size(max = 255, message = "The field length <description> cannot be more than 255 characters.")
  private String description;
  @NotNull(message = "The field <status> is required.")
  private Status status;
  @NotNull(message = "The field <departmentId> is required.")
  private Integer departmentId;
  
  private Integer parentId;
  
  private Integer id;

}