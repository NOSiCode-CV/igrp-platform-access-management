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
	@Size(min = 3, message = "The field length <name> must be at least 3 characters")
	@Size(max = 100, message = "The field length <name> cannot be more than 100 characters")
  
  private String name ;
  
  
  private MenuEntryType type ;
  
  
  private short position ;
  @Size(min = 1, message = "The field length <icon> must be at least 1 characters")
	@Size(max = 255, message = "The field length <icon> cannot be more than 255 characters")
  
  private String icon ;
  
  
  private Status status ;
  @Size(min = 3, message = "The field length <target> must be at least 3 characters")
	@Size(max = 10, message = "The field length <target> cannot be more than 10 characters")
  
  private String target ;
  @Size(min = 5, message = "The field length <url> must be at least 5 characters")
	@Size(max = 255, message = "The field length <url> cannot be more than 255 characters")
  
  private String url ;
  
  
  private String pageSlug ;
  
  
  private String parentCode ;
  
  
  private String applicationCode ;
  
  
  private String createdBy ;
  
  
  private String createdDate ;
  
  
  private String lastModifiedBy ;
  
  
  private String lastModifiedDate ;
  
  
  private List<String> roles = new ArrayList<>();

}