package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

@IgrpDTO
public class IGRPUserDTO {

  
  
  private Integer id;
  @NotBlank(message = "The field <name> is required.")
  
  private String name;
  @NotBlank(message = "The field <username> is required.")
  
  private String username;
  @NotBlank(message = "The field <email> is required.")
	@Email(message = "Invalid email format for field <email>.")
  
  private String email;

}