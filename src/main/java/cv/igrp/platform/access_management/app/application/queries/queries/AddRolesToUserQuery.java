package cv.igrp.platform.access_management.app.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.RoleUserDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddRolesToUserQuery implements Query {

  
  private RoleUserDTO roleuserdto;
  @NotNull(message = "The field <id> is required.")
  private Integer id;

}