package cv.igrp.platform.access_management.users.application.service;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service responsible for resolving user identity across multiple identifiers.
 *
 * Two distinct operations:
 * - resolveOrCreate: used ONLY by RespondUserInvitationCommandHandler
 * (invite-accept flow).
 * Creates user if not found — authorised because the user accepted a valid
 * invitation.
 * - resolveOrEnrich: used ONLY by UserIdentityResolutionListener (every
 * authenticated request).
 * Never creates. Only enriches existing users with missing JWT claims.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityResolutionService {

    private final IGRPUserEntityRepository userRepository;

    // -------------------------------------------------------------------------
    // INVITE-ACCEPT FLOW — may create user
    // -------------------------------------------------------------------------

    /**
     * Resolves an existing user or creates a new one.
     * MUST be called only from RespondUserInvitationCommandHandler.
     */
    @Transactional
    public IGRPUserEntity resolveOrCreate(String externalId, String email,
            String nic, String phoneNumber, String name) {
        String normEmail = normalize(email);
        String normNic = nic != null && !nic.isBlank() ? nic.trim().toUpperCase() : null;
        String normPhone = normalize(phoneNumber);
        // String normExtId = normalize(externalId);
        String normExtId = (externalId != null && !externalId.isBlank()) ? externalId.trim() : null;

        guardAllNull(normEmail, normExtId, normNic, normPhone);

        Optional<IGRPUserEntity> userOpt = userRepository.findByAnyIdentifier(normEmail, normExtId, normNic, normPhone);

        if (userOpt.isPresent()) {
            return enrich(userOpt.get(), normExtId, normEmail, normNic, normPhone, name);
        }

        // Not found — create. Authorised because the user accepted an invitation.
        IGRPUserEntity newUser = new IGRPUserEntity();
        newUser.setUsername(normExtId != null ? normExtId : normEmail);
        newUser.setExternalId(normExtId);
        newUser.setEmail(normEmail);
        newUser.setNic(normNic);
        newUser.setPhoneNumber(normPhone);
        newUser.setName(name);
        newUser.setStatus(Status.ACTIVE);

        try {
            log.info("[IDENTITY] Creating user via invite-accept: extId={}, email={}", normExtId, normEmail);
            return userRepository.save(newUser);
        } catch (DataIntegrityViolationException e) {
            log.warn("[IDENTITY] Race condition on creation, retrying lookup...");
            return userRepository.findByAnyIdentifier(normEmail, normExtId, normNic, normPhone)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * Async wrapper for resolveOrCreate — not used by listener.
     */
    @Async
    public void resolveOrCreateAsync(String externalId, String email,
            String nic, String phoneNumber, String name) {
        try {
            resolveOrCreate(externalId, email, nic, phoneNumber, name);
        } catch (Exception e) {
            log.warn("[IDENTITY] Async resolveOrCreate failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // LOGIN ENRICHMENT FLOW — never creates user
    // -------------------------------------------------------------------------

    /**
     * Enriches an existing user with missing JWT claims.
     * MUST be called only from UserIdentityResolutionListener.
     * If user does not exist, logs a warning and returns — no creation.
     */
    @Transactional
    public void resolveOrEnrich(String externalId, String email,
            String nic, String phoneNumber, String name) {
        String normEmail = normalize(email);
        String normNic = nic != null && !nic.isBlank() ? nic.trim().toUpperCase() : null;
        String normPhone = normalize(phoneNumber);
        // String normExtId = normalize(externalId);
        String normExtId = (externalId != null && !externalId.isBlank()) ? externalId.trim() : null;

        if (normEmail == null && normExtId == null && normNic == null && normPhone == null) {
            log.warn("[IDENTITY] Cannot enrich user — all identifiers null");
            return;
        }

        Optional<IGRPUserEntity> userOpt = userRepository.findByAnyIdentifier(normEmail, normExtId, normNic, normPhone);

        if (userOpt.isEmpty()) {
            log.info("[IDENTITY] No account found for externalId={} — enrichment skipped", externalId);
            return;
        }

        enrich(userOpt.get(), normExtId, normEmail, normNic, normPhone, name);
    }

    /**
     * Async wrapper for resolveOrEnrich — used by UserIdentityResolutionListener.
     */
    @Async
    public void resolveOrEnrichAsync(String externalId, String email,
            String nic, String phoneNumber, String name) {
        try {
            resolveOrEnrich(externalId, email, nic, phoneNumber, name);
        } catch (Exception e) {
            log.warn("[IDENTITY] Async enrichment failed: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Enriches null fields on the entity. Never overwrites existing data.
     * Saves only if at least one field changed.
     */
    private IGRPUserEntity enrich(IGRPUserEntity user, String normExtId, String normEmail,
            String normNic, String normPhone, String name) {
        boolean changed = false;

        if (user.getExternalId() == null && normExtId != null) {
            user.setExternalId(normExtId);
            changed = true;
        }
        if (user.getEmail() == null && normEmail != null) {
            user.setEmail(normEmail);
            changed = true;
        }
        if (user.getNic() == null && normNic != null) {
            user.setNic(normNic);
            changed = true;
        }
        if (user.getPhoneNumber() == null && normPhone != null) {
            user.setPhoneNumber(normPhone);
            changed = true;
        }
        if (user.getName() == null && name != null && !name.isBlank()) {
            user.setName(name);
            changed = true;
        }

        if (changed) {
            log.info("[IDENTITY] Enriching user: id={}, username={}", user.getId(), user.getUsername());
            return userRepository.save(user);
        }
        return user;
    }

    private static String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim().toLowerCase() : null;
    }

    private static void guardAllNull(String... values) {
        for (String v : values) {
            if (v != null)
                return;
        }
        log.warn("[IDENTITY] Cannot resolve user — all identifiers null");
        throw new IllegalArgumentException("At least one identifier must be provided");
    }
}