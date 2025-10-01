package cv.igrp.platform.access_management.shared.config;


import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class ApplicationAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            // Fallback when no user is authenticated (e.g., server-generated records)
            return Optional.of("system");
        }

        return authentication.getPrincipal() instanceof Jwt jwt ? Optional.of(jwt.getClaimAsString("preferred_username")) : Optional.ofNullable(authentication.getName());
    }

}