package cv.igrp.platform.access_management.users.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserQuery implements Query {

  
  private IGRPUserDTO igrpuserdto;

}