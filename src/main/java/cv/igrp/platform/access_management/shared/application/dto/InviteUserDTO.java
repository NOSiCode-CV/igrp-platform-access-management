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
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;
import cv.igrp.platform.access_management.shared.i18n.SupportedLocale;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class InviteUserDTO  {

  @NotBlank(message = "{validation.email.required}")
  @Email(message = "{validation.email.invalid}")
  private String email;
  
  
  private String departmentCode ;
  
  
  private List<String> roles = new ArrayList<>();

  /**
   * Optional language of the invitee (FR-28/FR-29): {@code pt}, {@code en} or
   * {@code fr} (regional variants are accepted and normalized). Every invitation
   * email is sent in this language and it becomes the new user's
   * {@code metadata.locale} on acceptance. Omit to use the configured default.
   */
  @SupportedLocale
  private String locale;

}