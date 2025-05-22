package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();

            if (principal instanceof IGRPUserDTO userDetails) {
                return userDetails.getId(); // ou getUserId() – depende da tua classe
            }
        }
        throw new RuntimeException("User not authenticated");
    }
}
