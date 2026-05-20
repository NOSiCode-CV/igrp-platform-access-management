package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Custom {@link OidcUserService} that maps an external OpenID subject to an
 * internal {@link IGRPUserEntity}, provisioning one on the first login and
 * stamping federated attributes onto the user's metadata column.
 *
 * <h3>Security gates (OWASP A04 / A07)</h3>
 * <ul>
 *   <li><b>email_verified</b> — the upstream IdP must assert the email address
 *       is verified ({@code igrp.oauth.external-idp.require-email-verified},
 *       default {@code true}). Logins from IdPs that do not set this claim
 *       (or set it to {@code false}) are rejected with
 *       {@code error=unverified_email}.</li>
 *   <li><b>Email domain allowlist</b> — when
 *       {@code igrp.oauth.external-idp.allowed-email-domains} is set to a
 *       non-empty comma-separated list, only addresses whose domain suffix is
 *       in the list are admitted. Useful for restricting federation to
 *       organisational accounts when the upstream IdP allows self-registration
 *       (e.g. Google Workspace, GitHub).</li>
 *   <li><b>Minimum ACR</b> — when
 *       {@code igrp.oauth.external-idp.required-acr-values} is set, the
 *       upstream ID Token must carry one of the listed ACR values. This
 *       enforces step-up authentication (e.g. MFA) for all federated logins
 *       or, when combined with per-client checks, for high-privilege scopes.
 *       </li>
 * </ul>
 */
@Service
public class IgrpOidcUserService extends OidcUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IgrpOidcUserService.class);

    private final UserIdentityJpaRepository userIdentityRepository;
    private final IGRPUserEntityRepository userRepository;
    private final SessionAuditLogger sessionAuditLogger;

    // --- OWASP A04/A07 security gate configuration ---

    /**
     * When {@code true} (default), logins whose upstream ID Token does not
     * carry {@code email_verified: true} are rejected.
     */
    @Value("${igrp.oauth.external-idp.require-email-verified:true}")
    private boolean requireEmailVerified;

    /**
     * Comma-separated list of allowed email domains (e.g. {@code igrp.cv,nosi.cv}).
     * When blank (default) all domains are accepted.
     */
    @Value("${igrp.oauth.external-idp.allowed-email-domains:}")
    private String allowedEmailDomainsRaw;

    /**
     * Comma-separated list of ACR values that are acceptable for federated
     * logins (e.g. {@code pwd,cmdcv,cni}). When blank (default) any ACR is
     * accepted. At least one of the listed values must appear in the ID Token's
     * {@code acr} claim.
     */
    @Value("${igrp.oauth.external-idp.required-acr-values:}")
    private String requiredAcrValuesRaw;

    public IgrpOidcUserService(UserIdentityJpaRepository userIdentityRepository,
                               IGRPUserEntityRepository userRepository,
                               SessionAuditLogger sessionAuditLogger) {
        this.userIdentityRepository = userIdentityRepository;
        this.userRepository = userRepository;
        this.sessionAuditLogger = sessionAuditLogger;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidcUser = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId();
        String externalUserId = oidcUser.getSubject();
        Map<String, Object> attributes = oidcUser.getAttributes();
        String email = oidcUser.getEmail();

        // --- Security gate 1: email_verified (OWASP A07) ---
        enforceEmailVerified(attributes, externalUserId);

        // --- Security gate 2: email domain allowlist (OWASP A04) ---
        enforceEmailDomain(email, externalUserId);

        // --- Security gate 3: minimum ACR (OWASP A07) ---
        enforceAcr(oidcUser, externalUserId);

        Optional<UserIdentityEntity> existing = userIdentityRepository
                .findByProviderAndUserId(provider, externalUserId);

        IGRPUserEntity user = existing
                .map(UserIdentityEntity::getUser)
                .orElseGet(() -> provisionUser(provider, externalUserId, attributes));

        updateUserMetadataFromClaims(user, attributes);
        userRepository.save(user);

        LOGGER.debug("Federated OIDC login mapped: provider={} sub={} -> internalId={}",
                provider, externalUserId, user.getId());

        return new IgrpOidcUser(
                oidcUser.getAuthorities(),
                request.getIdToken(),
                buildUserProfile(oidcUser)
        );
    }

    // -------------------------------------------------------------------------
    // Security gate implementations
    // -------------------------------------------------------------------------

    /**
     * OWASP A07 — Rejects federated logins where the upstream IdP has not
     * verified the email address. Many social providers (GitHub, Google) set
     * {@code email_verified: false} for accounts that registered with an
     * unconfirmed address. Accepting such accounts could allow spoofed
     * identities to gain platform access.
     */
    private void enforceEmailVerified(Map<String, Object> attributes, String sub) {
        if (!requireEmailVerified) {
            return;
        }
        Object ev = attributes.get("email_verified");
        if (!Boolean.TRUE.equals(ev)) {
            LOGGER.warn("[OIDC-SEC] Rejected federated login sub={}: email_verified is not true (value={})",
                    sub, ev);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unverified_email",
                            "The identity provider has not verified the email address. "
                                    + "Please verify your email and try again.",
                            null));
        }
    }

    /**
     * OWASP A04 — Restricts federated auto-provisioning to addresses whose
     * domain is in the configured allowlist. When the list is empty all
     * domains are accepted (open federation). When a non-empty list is
     * configured, only organisational accounts from known domains can log in,
     * which prevents arbitrary users from self-registering through an upstream
     * IdP that allows open sign-up (e.g. Google Workspace, GitHub).
     */
    private void enforceEmailDomain(String email, String sub) {
        if (!StringUtils.hasText(allowedEmailDomainsRaw)) {
            return; // no restriction configured
        }

        Set<String> allowedDomains = Arrays.stream(allowedEmailDomainsRaw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (allowedDomains.isEmpty()) {
            return;
        }

        if (!StringUtils.hasText(email)) {
            LOGGER.warn("[OIDC-SEC] Rejected federated login sub={}: no email claim present", sub);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email",
                            "The identity provider did not supply an email address. "
                                    + "Include the 'email' scope and try again.",
                            null));
        }

        String domain = email.toLowerCase().contains("@")
                ? email.toLowerCase().substring(email.indexOf('@') + 1)
                : "";

        boolean allowed = allowedDomains.stream().anyMatch(domain::endsWith);
        if (!allowed) {
            LOGGER.warn("[OIDC-SEC] Rejected federated login sub={} email-domain={}: not in allowlist",
                    sub, domain);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("unauthorized_email_domain",
                            "Your email domain is not permitted to access this platform.",
                            null));
        }
    }

    /**
     * OWASP A07 — Enforces a minimum authentication context (ACR) for all
     * federated logins. When {@code igrp.oauth.external-idp.required-acr-values}
     * is set the upstream ID Token's {@code acr} claim must match at least one
     * of the listed values. This ensures that, for example, MFA-protected logins
     * can be distinguished from password-only logins and that step-up requirements
     * are enforced at the federation boundary.
     */
    private void enforceAcr(OidcUser oidcUser, String sub) {
        if (!StringUtils.hasText(requiredAcrValuesRaw)) {
            return; // no ACR requirement configured
        }

        Set<String> requiredAcr = Arrays.stream(requiredAcrValuesRaw.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        if (requiredAcr.isEmpty()) {
            return;
        }

        String acr = oidcUser.getClaimAsString("acr");
        if (!StringUtils.hasText(acr) || !requiredAcr.contains(acr.toLowerCase())) {
            LOGGER.warn("[OIDC-SEC] Rejected federated login sub={}: acr={} not in required set {}",
                    sub, acr, requiredAcr);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("insufficient_authentication_context",
                            "The authentication method used by the identity provider does not "
                                    + "meet the required authentication context for this platform.",
                            null));
        }
    }

    // -------------------------------------------------------------------------
    // User provisioning
    // -------------------------------------------------------------------------

    private IGRPUserEntity provisionUser(String provider, String externalUserId, Map<String, Object> attributes) {
        String preferredUsername = (String) attributes.getOrDefault("preferred_username", externalUserId);
        String email = (String) attributes.get("email");
        String name = (String) attributes.getOrDefault("name", preferredUsername);

        IGRPUserEntity user = userRepository.findByUsername(externalUserId)
                .orElseGet(IGRPUserEntity::new);
        if (user.getId() == null) {
            user.setUsername(preferredUsername);
            user.setEmail(email);
            user.setName(name);
            // Phase G3: first OIDC login provisions a TEMPORARY user; the
            // invitation flow promotes to ACTIVE.
            // email_verified has already been checked by enforceEmailVerified() above.
            user.setStatus(Status.TEMPORARY);
            user.setEmailVerified(Boolean.TRUE.equals(attributes.get("email_verified")));
            user = userRepository.save(user);
            LOGGER.info("Provisioned new IGRP user from provider={} sub={}", provider, externalUserId);
            // Phase G3: NFR-4 audit row for first-login provisioning.
            try {
                sessionAuditLogger.recordUserStatusTransitioned(
                        user.getId(), null, Status.TEMPORARY.getCode(),
                        SessionAuditLogger.SYSTEM, "FIRST_LOGIN");
            } catch (Exception auditEx) {
                LOGGER.warn("[G3] first-login audit failed for sub={}: {}", externalUserId, auditEx.getMessage());
            }
        }

        UserIdentityEntity identity = new UserIdentityEntity();
        identity.setId(UUID.randomUUID());
        identity.setProvider(provider);
        identity.setUserId(externalUserId);
        identity.setConnection(provider + "-oidc");
        identity.setUser(user);
        userIdentityRepository.save(identity);

        return user;
    }

    // -------------------------------------------------------------------------
    // Claim mapping helpers
    // -------------------------------------------------------------------------

    private void updateUserMetadataFromClaims(IGRPUserEntity user, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        Map<String, Object> metadata = user.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        // Standard OIDC claims to lift into user metadata; omit sub to avoid duplication.
        for (String key : new String[]{"name", "given_name", "family_name", "preferred_username", "email",
                "email_verified", "locale", "picture", "phone_number"}) {
            Object value = attributes.get(key);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        user.setMetadata(metadata);
    }

    private UserProfile buildUserProfile(OidcUser oidcUser) {
        Map<String, Object> attributes = oidcUser.getAttributes();
        List<String> amr = oidcUser.getClaimAsStringList("amr");
        String acr = oidcUser.getClaimAsString("acr");

        return new UserProfile(
                oidcUser.getSubject(),
                oidcUser.getIssuer() != null ? oidcUser.getIssuer().toString() : null,
                firstNonBlank(oidcUser.getFullName(), oidcUser.getClaimAsString("name")),
                oidcUser.getEmail(),
                oidcUser.getClaimAsString("phone_number"),
                resolveNic(attributes, acr),
                resolveAuthMethod(amr, acr),
                amr
        );
    }

    private String resolveNic(Map<String, Object> attributes, String acr) {
        if ("cni".equalsIgnoreCase(acr)) {
            return attributes.get("sub") != null ? String.valueOf(attributes.get("sub")) : null;
        }
        Object nic = attributes.get("nic");
        return nic != null ? String.valueOf(nic) : null;
    }

    private String resolveAuthMethod(List<String> amr, String acr) {
        if (amr != null && amr.contains("BasicAuthenticator") && "pwd".equalsIgnoreCase(acr)) {
            return "EMAIL";
        }
        if (amr != null && amr.contains("OpenIDConnectAuthenticator") && "cmdcv".equalsIgnoreCase(acr)) {
            return "CMDCV";
        }
        if (amr != null && amr.contains("OpenIDConnectAuthenticator") && "cni".equalsIgnoreCase(acr)) {
            return "CNI";
        }
        return "UNKNOWN";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
