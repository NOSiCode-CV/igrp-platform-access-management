package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();

            if (principal instanceof CustomUserDetails userDetails) {
                return userDetails.getId(); // ou getUserId() – depende da tua classe
            }
        }
        throw new RuntimeException("User not authenticated");
    }
}
