/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class InvitationDTO  {

  
  
  private Integer id ;
  
  
  private String email ;
  
  
  private InvitationStatus status ;
  
  
  private LocalDateTime expiry ;
  
  
  private LocalDateTime invitationDate ;
  
  
  private String invitedBy ;
  
  
  private String comments ;
  
  
  private String invitationUrl ;

}