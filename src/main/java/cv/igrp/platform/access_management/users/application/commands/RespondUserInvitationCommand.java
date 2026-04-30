package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.dto.UserInvitationResponseDTO;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespondUserInvitationCommand implements Command {

  
  private UserInvitationResponseDTO userinvitationresponsedto;
  
  @NotBlank(message = "The field <token> is required")
  private String token;

}