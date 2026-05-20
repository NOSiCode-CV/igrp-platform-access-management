package cv.igrp.platform.access_management.users.application.service;

import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

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
    private final SessionAuditLogger sessionAuditLogger;

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

        Optional<IGRPUserEntity> userOpt = findByAnyIdentifier(normEmail, normExtId, normNic, normPhone);

        if (userOpt.isPresent()) {
            return enrich(userOpt.get(), normExtId, normEmail, normNic, normPhone, name);
        }

        // Not found — create. Provision a TEMPORARY user on first OIDC login;
        // the invitation flow promotes to ACTIVE (Phase G3).
        IGRPUserEntity newUser = new IGRPUserEntity();
        newUser.setUsername(bestUsername(normExtId, normEmail));
        // externalId field removed in G2 — username retains the canonical sub mapping
        newUser.setEmail(normEmail);
        newUser.setNic(normNic);
        newUser.setPhoneNumber(normPhone);
        newUser.setName(name);
        newUser.setStatus(Status.TEMPORARY);

        try {
            log.info("[IDENTITY] Creating user via invite-accept: extId={}, email={}", normExtId, normEmail);
            IGRPUserEntity saved = userRepository.save(newUser);
            // Phase G3: NFR-4 audit row for first-login provisioning.
            try {
                sessionAuditLogger.recordUserStatusTransitioned(
                        saved.getId(), null, Status.TEMPORARY.getCode(),
                        SessionAuditLogger.SYSTEM, "FIRST_LOGIN");
            } catch (Exception auditEx) {
                log.warn("[IDENTITY] first-login audit failed: {}", auditEx.getMessage());
            }
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("[IDENTITY] Race condition on creation, retrying lookup...");
            return findByAnyIdentifier(normEmail, normExtId, normNic, normPhone)
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

        Optional<IGRPUserEntity> userOpt = findByAnyIdentifier(normEmail, normExtId, normNic, normPhone);

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

        // externalId field removed in G2 — no-op
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

    /**
     * Choose the best username for a new user.
     * Keycloak sub is a UUID — use email instead.
     * Autentika sub is email or NIC — use it directly.
     */
    private static String bestUsername(String normExtId, String normEmail) {
        if (normExtId != null && normExtId.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            // Keycloak UUID sub — prefer email as human-readable username
            return normEmail != null ? normEmail : normExtId;
        }
        // Autentika: sub is already email or NIC
        return normExtId != null ? normExtId : normEmail;
    }

    private static String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim().toLowerCase() : null;
    }

    private Optional<IGRPUserEntity> findByAnyIdentifier(String email, String externalId, String nic, String phoneNumber) {
        if (externalId != null) {
            // externalId field removed in G2 — only check by username (the canonical sub)
            Optional<IGRPUserEntity> byUsername =
                    findOneSafely("username", externalId, () -> userRepository.findByUsername(externalId));
            if (byUsername.isPresent()) {
                return byUsername;
            }
        }

        if (nic != null) {
            Optional<IGRPUserEntity> byNic =
                    findOneSafely("nic", nic, () -> userRepository.findByNicIgnoreCase(nic));
            if (byNic.isPresent()) {
                return byNic;
            }
        }

        if (phoneNumber != null) {
            Optional<IGRPUserEntity> byPhone =
                    findOneSafely("phone", phoneNumber, () -> userRepository.findByPhoneNumber(phoneNumber));
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }

        if (email != null) {
            return findOneSafely("email", email, () -> userRepository.findByEmailIgnoreCase(email));
        }

        return Optional.empty();
    }

    /**
     * Wraps a {@code findByXxx} call that returns {@code Optional<IGRPUserEntity>}
     * so that when bad data leaves two rows behind the lookup logs a warning
     * and yields {@link Optional#empty()} instead of letting
     * {@link IncorrectResultSizeDataAccessException} bubble up and abort
     * async enrichment for the entire request. Enrichment is best-effort —
     * a duplicate row should never block authentication.
     */
    private Optional<IGRPUserEntity> findOneSafely(String identifierKind,
                                                   String identifierValue,
                                                   Supplier<Optional<IGRPUserEntity>> lookup) {
        try {
            return lookup.get();
        } catch (IncorrectResultSizeDataAccessException ex) {
            log.warn("[IDENTITY] Duplicate match for {}='{}' ({} rows) — skipping this identifier",
                    identifierKind, identifierValue, ex.getActualSize());
            return Optional.empty();
        }
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
