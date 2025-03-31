package cv.igrp.platform.access_management.shared.config;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class ApplicationAuditorAware implements AuditorAware<String> {

  @Value("${spring.profiles.active}")
  private String activeProfile;

  @Override
  public Optional<String> getCurrentAuditor() {
    var preferredUsername = getPreferredUsername();
    return Optional.of(preferredUsername);
  }

  /**
  * Retrieves the preferred username from the JWT token in the security context.
  *
  * @return preferred username
  * @throws IllegalStateException if the JWT token is not found in the security context
  */
  public String getPreferredUsername() {

    if ("development".equals(activeProfile) || "staging".equals(activeProfile)) {
      return "";
    }

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return jwt.getClaimAsString("preferred_username");
    }

    throw new IllegalStateException("JWT token not found in security context");
  }

}