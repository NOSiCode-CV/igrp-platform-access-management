package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncApplicationsCommand implements Command {

  
  private ApplicationDTO applicationdto;

}