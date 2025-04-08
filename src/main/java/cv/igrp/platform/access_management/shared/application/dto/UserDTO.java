package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Collection;
@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class UserDTO {

  
  private String igrpUsername;
  
  private String fullname;
  
  private String email;
  
  private Collection<String> roles;
  
  private Collection<String> departments;
  
  private Collection<String> apps;
  
  private String imageUrl;
  
  private String signatureUrl;
  
  private String status;

}