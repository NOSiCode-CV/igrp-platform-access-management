package cv.igrp.platform.access_management.app.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateApplicationQuery implements Query {

  
  private ApplicationDTO applicationdto;

}