package cv.igrp.platform.access_management.app.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;


import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class RecentApplicationDTO {

  
  private Integer appId;
  
  private LocalDateTime lastAccess;

}