package cv.igrp.platform.access_management.menu_entry.application.queries.queries;

import cv.igrp.framework.core.domain.Query;
import jakarta.validation.constraints.*;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateMenuQuery implements Query {

  
  private MenuEntryDTO menuentrydto;

}