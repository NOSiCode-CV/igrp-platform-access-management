/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.authorization.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@IgrpDTO
public record PermissionCacheEntryDTO (
  
  boolean allowed, 
  
  List<String> viaRoles, 
  
  String reason
){
    public PermissionCacheEntryDTO {
        viaRoles = viaRoles != null ? viaRoles : Collections.emptyList();
    }
}