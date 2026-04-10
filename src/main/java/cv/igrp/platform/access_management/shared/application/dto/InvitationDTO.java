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
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import java.time.LocalDateTime;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class InvitationDTO  {

  
  
  private Integer id ;
  
  private IdentifierType identifierType ;
  
  
  private String identifierValue ;
  
  
  private List<String> allowedAuthMethods = new ArrayList<>();
  
  
  private InvitationStatus status ;
  
  
  private LocalDateTime expiry ;
  
  
  private LocalDateTime invitationDate ;
  
  
  private String invitedBy ;
  
  
  private String comments ;
  
  
  private String invitationUrl ;
  
  @Valid
  private List<CodeDescriptionDTO> roles = new ArrayList<>();
  
  @Valid
  private CodeDescriptionDTO department ;

}