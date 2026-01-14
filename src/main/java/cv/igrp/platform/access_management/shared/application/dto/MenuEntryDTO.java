/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class MenuEntryDTO  {

  
  
  private Integer id ;
  
  
  private String code ;
  @NotBlank(message = "The field <name> is required")
	@Size(max = 255, message = "The field length <name> cannot be more than 255 characters")
  
  private String name ;
  
  
  private MenuEntryType type ;
  
  
  private short position ;
  @Size(max = 255, message = "The field length <icon> cannot be more than 255 characters")
  
  private String icon ;
  
  
  private Status status ;
  @Size(max = 50, message = "The field length <target> cannot be more than 50 characters")
  
  private String target ;
  @Size(max = 255, message = "The field length <url> cannot be more than 255 characters")
  
  private String url ;
  
  
  private String pageSlug ;
  
  
  private String parentCode ;
  
  
  private String applicationCode ;
  
  
  private String createdBy ;
  
  
  private String createdDate ;
  
  
  private String lastModifiedBy ;
  
  
  private String lastModifiedDate ;
  
  @Valid
  private List<RoleDepartmentDTO> roles = new ArrayList<>();

}