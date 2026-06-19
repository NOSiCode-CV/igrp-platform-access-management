/* THIS FILE WAS GENERATED AUTOMATICALLY BY iGRP STUDIO. */
/* DO NOT MODIFY IT BECAUSE IT COULD BE REWRITTEN AT ANY TIME. */

package cv.igrp.platform.access_management.shared.application.dto;

import cv.igrp.framework.stereotype.IgrpDTO;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import cv.igrp.platform.access_management.shared.application.constants.Status;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor


@IgrpDTO
public class IGRPUserDTO  {



  private String id ;


  private String name ;


  private String username ;
  @NotBlank(message = "The field <email> is required")
	@Email(message = "Invalid email format for field <email>")

  private String email ;


  private Status status ;


  private String picture ;


  private String signature ;

  // Optional. `@Size(max = 13)` does NOT fire when the value is null (Bean
  // Validation spec): a request body that omits `nic` entirely, or sends
  // `nic: null`, passes validation untouched. The constraint only triggers
  // when a non-null string longer than 13 chars is supplied. Studio metadata
  // (.igrpstudio/shared/dto/IGRPUserDTO.json) also declares `required: false`,
  // so no `@NotBlank` will be re-added on the next Studio regeneration.
  @Size(max = 13, message = "The field <nic> must be at most 13 characters")
  private String nic;

  // Optional. `@Pattern` is skipped on null (Bean Validation spec); only a
  // non-null phone number is required to match the E.164 shape. NOTE: empty
  // string ("") will fail this pattern — callers that mean "no phone" should
  // send `null` or omit the field entirely, not `""`.
  @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "The field <phoneNumber> must follow E.164 format (e.g., +1234567890)")
  private String phoneNumber;

  /**
   * Mirrors {@code t_user.email_verified}. True once the user's email has
   * passed the OTP / invitation-acceptance verification flow. Defaults to
   * {@code false} on a freshly invited account.
   */
  private Boolean emailVerified;

  /**
   * Free-form metadata mirrored from {@code t_user.metadata} (jsonb).
   * Exposed through OAuth user-management APIs and enriched into issued
   * JWTs by the authorization server. Keys are namespaced by the domain
   * that owns them (e.g. {@code billing.last_invoice_date}); leave
   * {@code null} or empty when the caller has no metadata to provide.
   */
  private Map<String, Object> metadata = new LinkedHashMap<>();

}