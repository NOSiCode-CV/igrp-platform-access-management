/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class ResourceItemDTO  {

  
  
  private Integer id ;
  @NotBlank(message = "The field <name> is required")
	@Size(max = 255, message = "The field length <name> cannot be more than 255 characters")
  
  private String name ;
  
  
  private String description ;
  @Size(max = 255, message = "The field length <url> cannot be more than 255 characters")
  
  private String url ;
  @NotBlank(message = "The field <resourceName> is required")
  
  private String resourceName ;
  
  
  private List<String> permissions = new ArrayList<>();
  
  
  private String createdBy ;
  
  
  private String createdDate ;
  
  
  private String lastModifiedBy ;
  
  
  private String lastModifiedDate ;

}