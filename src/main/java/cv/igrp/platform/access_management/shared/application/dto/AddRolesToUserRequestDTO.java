package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@IgrpDTO
public class AddRolesToUserRequestDTO {
   
   @NotEmpty(message = "{validation.roles.required}")
   private List<String> roles;
   
   @Future(message = "{validation.expiresAt.future}")
   private LocalDateTime expiresAt;
}
