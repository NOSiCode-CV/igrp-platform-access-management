package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.Collection;

@IgrpDTO
public record IGRPUserDTO (
  @NotNull(message = "The field <id> is required.")
  Integer id, 
  @NotBlank(message = "The field <name> is required.")
  String name, 
  @NotBlank(message = "The field <username> is required.")
  Collection<String> username, 
  @NotBlank(message = "The field <email> is required.")
	@Email(message = "Invalid email format for field <email>.")
  String email
){}