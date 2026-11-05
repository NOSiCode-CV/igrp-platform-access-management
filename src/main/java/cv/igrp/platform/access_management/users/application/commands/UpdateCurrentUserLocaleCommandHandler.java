package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.i18n.IgrpLocale;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.shared.security.SubjectParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores the current user's language preference as {@code metadata.locale}
 * (FR-10/FR-11). The value is normalized to the platform base tag
 * ({@code pt-CV} → {@code pt}); anything outside the platform list is rejected
 * with {@code 400 INVALID_LOCALE}. Every other metadata key is preserved.
 *
 * <p>The preference is surfaced to clients through {@code GET /api/users/me}
 * ({@code metadata.locale}) and the OIDC {@code locale} claim.
 */
@Component
public class UpdateCurrentUserLocaleCommandHandler
        implements CommandHandler<UpdateCurrentUserLocaleCommand, ResponseEntity<Void>> {

    /** Key of the language preference inside {@code IGRPUserEntity.metadata}. */
    public static final String METADATA_LOCALE_KEY = "locale";

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateCurrentUserLocaleCommandHandler.class);

    private final AuthenticationHelper authenticationHelper;
    private final IGRPUserEntityRepository userRepository;

    public UpdateCurrentUserLocaleCommandHandler(AuthenticationHelper authenticationHelper,
                                                 IGRPUserEntityRepository userRepository) {
        this.authenticationHelper = authenticationHelper;
        this.userRepository = userRepository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<Void> handle(UpdateCurrentUserLocaleCommand command) {
        String requested = command.getLocale();
        IgrpLocale locale = IgrpLocale.fromTag(requested)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        IgrpErrorCode.INVALID_LOCALE, requested, IgrpLocale.supportedTags()));

        String sub = authenticationHelper.getSub();
        String userId = SubjectParser.parseUserSubjectOrThrow(sub);
        IGRPUserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> IgrpResponseStatusException.of(
                        IgrpErrorCode.IGRP_AUTH_USER_NOT_FOUND_BY_EXTERNAL_ID, sub));

        Map<String, Object> metadata = user.getMetadata() != null
                ? new LinkedHashMap<>(user.getMetadata())
                : new LinkedHashMap<>();
        metadata.put(METADATA_LOCALE_KEY, locale.tag());
        user.setMetadata(metadata);
        userRepository.save(user);

        LOGGER.info("User id={} changed language preference to {}", userId, locale.tag());
        return ResponseEntity.noContent().build();
    }
}
