package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Phase G3 — SpEL bean exposed as {@code @userStatusGuard} for use in
 * {@code @PreAuthorize} expressions on controllers that must distinguish
 * between fully-onboarded {@link Status#ACTIVE} users and not-yet-onboarded
 * {@link Status#TEMPORARY} users.
 *
 * <p>Two distinct gates:
 * <ul>
 *   <li>{@link #requiresActive(Authentication)} — passes only for ACTIVE users.
 *       Default gate for the majority of endpoints.</li>
 *   <li>{@link #requiresActiveOrTemporary(Authentication)} — also lets
 *       TEMPORARY users in. Used on the invitation-response endpoints and
 *       {@code /api/users/me} so a user can complete onboarding while their
 *       account is still TEMPORARY.</li>
 * </ul>
 *
 * <p>DELETED / INACTIVE always return {@code false}. Null authentication,
 * non-JWT principal, missing or non-numeric {@code sub}, and missing user
 * all return {@code false} — fail-closed.
 */
@Component("userStatusGuard")
public class UserStatusGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserStatusGuard.class);

    private final IGRPUserEntityRepository userRepository;

    public UserStatusGuard(IGRPUserEntityRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean requiresActive(Authentication auth) {
        return resolveStatus(auth)
                .map(s -> s == Status.ACTIVE)
                .orElse(false);
    }

    public boolean requiresActiveOrTemporary(Authentication auth) {
        return resolveStatus(auth)
                .map(s -> s == Status.ACTIVE || s == Status.TEMPORARY)
                .orElse(false);
    }

    private Optional<Status> resolveStatus(Authentication auth) {
        if (auth == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (!(principal instanceof Jwt jwt)) {
            return Optional.empty();
        }
        Integer userId;
        try {
            userId = SubjectParser.parseUserSubjectOrThrow(jwt.getSubject());
        } catch (InvalidPrincipalException ex) {
            LOGGER.debug("[UserStatusGuard] non-user principal — denying: {}", ex.getMessage());
            return Optional.empty();
        }
        return userRepository.findById(userId).map(IGRPUserEntity::getStatus);
    }
}
