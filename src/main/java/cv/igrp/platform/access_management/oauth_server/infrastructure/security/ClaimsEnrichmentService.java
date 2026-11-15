package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.shared.infrastructure.service.RootUser;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;


/**
 * Builds the iGRP-specific claims injected into issued JWTs (v3-25, #33; spec
 * "Tokens"). Tokens say only who the caller is, so their size no longer
 * depends on permissions:
 * <ul>
 *     <li>{@code sub}: mapped to the internal {@link IGRPUserEntity} id
 *         when the caller authenticated through a federated provider;</li>
 *     <li>access token: {@code is_super_admin}, {@code name},
 *         {@code preferred_username}, {@code email}; a service account's
 *         identifying claims ({@link ServiceAccountTokenClaims});</li>
 *     <li>ID token (and userinfo): {@code is_super_admin} and the standard
 *         profile the granted scopes allow.</li>
 * </ul>
 * No permission, role, department or metadata claim is ever added: services
 * read the caller's permission set.
 */
@Service
public class ClaimsEnrichmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimsEnrichmentService.class);
    private static final String CLAIM_GIVEN_NAME = "given_name";
    private static final String CLAIM_FAMILY_NAME = "family_name";
    private static final String CLAIM_PREFERRED_USERNAME = "preferred_username";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_EMAIL_VERIFIED = "email_verified";
    private static final String CLAIM_PICTURE = "picture";
    private static final String CLAIM_PHONE_NUMBER = "phone_number";
    private static final String CLAIM_LOCALE = "locale";
    private static final String CLAIM_IS_SUPER_ADMIN = "is_super_admin";
    /** The identity claims an access token keeps, when the scopes grant them. */
    private static final List<String> ACCESS_TOKEN_IDENTITY_CLAIMS =
            List.of("name", CLAIM_PREFERRED_USERNAME, CLAIM_EMAIL);

    private final OAuthClientJpaRepository oauthClientRepository;
    private final ServiceAccountJpaRepository serviceAccountRepository;
    private final UserIdentityJpaRepository userIdentityRepository;
    private final IGRPUserEntityRepository userRepository;

    public ClaimsEnrichmentService(OAuthClientJpaRepository oauthClientRepository,
                                   ServiceAccountJpaRepository serviceAccountRepository,
                                   UserIdentityJpaRepository userIdentityRepository,
                                   IGRPUserEntityRepository userRepository) {
        this.oauthClientRepository = oauthClientRepository;
        this.serviceAccountRepository = serviceAccountRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolve the internal IGRP user id given a federated (provider, sub) pair.
     * Returns {@code null} if no mapping is found.
     */
    @Transactional(readOnly = true)
    public String mapSubject(String provider, String externalUserId) {
        if (provider == null || externalUserId == null) {
            return null;
        }
        // Mirror mapEmail's status filter: only ACTIVE / TEMPORARY users are
        // admitted to the platform via the federated-login path. DELETED and
        // INACTIVE users must not be silently re-activated by a successful
        // upstream login. Without this, a DELETED user's UUID would land in
        // the JWT `sub` and every authorization check would deny — a token
        // that authenticates but never authorizes.
        return userIdentityRepository
                .findActiveByProviderAndUserId(provider, externalUserId)
                .map(UserIdentityEntity::getUser)
                .map(IGRPUserEntity::getId)
                .map(String::valueOf)
                .orElse(null);
    }

    /**
     * Resolve the internal IGRP user id given a federated provider and email.
     * Returns {@code null} if no matching local user is found, OR if the
     * matching user is administratively disabled (INACTIVE) / soft-deleted
     * (DELETED). Only {@code ACTIVE} and {@code TEMPORARY} users are admitted
     * to the platform via the federated-login path — INACTIVE accounts must
     * not be silently re-activated by a successful upstream login.
     *
     * <p>Uses the List-returning repository variant so a duplicate-email row
     * (data corruption, an ACTIVE/TEMPORARY pair, etc.) doesn't throw
     * {@code IncorrectResultSizeDataAccessException} mid-login; the
     * repository orders the candidates so ACTIVE wins over TEMPORARY and
     * the oldest id breaks remaining ties.
     */
    @Transactional(readOnly = true)
    public String mapEmail(String provider, String email) {
        if (provider == null || email == null) {
            return null;
        }
        return userRepository
                .findActiveOrTemporaryByEmailIgnoreCase(email)
                .stream()
                .findFirst()
                .map(IGRPUserEntity::getId)
                .map(String::valueOf)
                .orElse(null);
    }

    /**
     * Scopes configured on the given client. Empty set if the client is not
     * persisted (should not occur inside the authorization server pipeline).
     */
    @Transactional(readOnly = true)
    public Set<String> getScopesByClientId(String clientId) {
        return oauthClientRepository.findByClientId(clientId)
                .map(OAuthClientEntity::getScopes)
                .orElseGet(Collections::emptySet);
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveServiceAccountSubject(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return serviceAccountRepository.findByOauthClient_ClientId(clientId)
                .filter(this::isUsableServiceAccount)
                .map(ServiceAccountEntity::getId)
                .map(UUID::toString);
    }

    /**
     * The claims this service adds to an access token (v3-25, #33; spec "Tokens"):
     * who the caller is, never what it may do. For a user, {@code is_super_admin}
     * and, as the granted scopes allow, {@code name}, {@code preferred_username}
     * and {@code email}. For a service account, what identifies it as one.
     * Permissions are read from the permission set, not from the token.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildClaims(String subject, String clientId, Set<String> grantedScopes) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Set<String> scopes = grantedScopes != null ? new HashSet<>(grantedScopes) : Collections.emptySet();

        Optional<IGRPUserEntity> user = resolveUser(subject);
        Optional<ServiceAccountEntity> serviceAccount = user.isPresent()
                ? Optional.empty()
                : resolveServiceAccount(subject);

        claims.put(CLAIM_IS_SUPER_ADMIN, isSuperAdmin(user));
        Map<String, Object> identity = standardIdentityClaims(user, scopes);
        ACCESS_TOKEN_IDENTITY_CLAIMS.forEach(name -> {
            if (identity.containsKey(name)) {
                claims.put(name, identity.get(name));
            }
        });

        serviceAccount.ifPresent(account -> {
            claims.put(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE,
                    ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT);
            claims.put(ServiceAccountTokenClaims.CLAIM_SERVICE_ACCOUNT_ID, account.getId().toString());
            claims.put(ServiceAccountTokenClaims.CLAIM_CLIENT_ID, clientId);
            claims.put(ServiceAccountTokenClaims.CLAIM_APPLICATION_CODE, resolveApplicationCode(account));
        });

        LOGGER.debug("Enriched claims for subject={} clientId={} -> keys={}",
                subject, clientId, claims.keySet());
        return claims;
    }

    /**
     * The claims this service adds to a user's ID token: {@code is_super_admin}
     * and the profile the granted scopes allow (name, username, picture, email,
     * phone...). The userinfo endpoint serves the same. Empty for anyone else.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildIdTokenClaims(String subject, Set<String> grantedScopes) {
        Optional<IGRPUserEntity> user = resolveUser(subject);
        if (user.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(CLAIM_IS_SUPER_ADMIN, isSuperAdmin(user));
        claims.putAll(standardIdentityClaims(user,
                grantedScopes != null ? grantedScopes : Collections.emptySet()));
        return claims;
    }

    /**
     * Resolve the application code to stamp on the token. Prefers the
     * service account's own {@code application_id} FK (which lets a single
     * OAuth client back service accounts across multiple applications, if
     * the deployment ever needs that). Falls back to the OAuth client's own
     * application. Returns an empty string when neither is set — consumers
     * treat that as "no application binding" rather than reject the token.
     */
    private String resolveApplicationCode(ServiceAccountEntity account) {
        if (account == null) return "";
        if (account.getApplication() != null && account.getApplication().getCode() != null) {
            return account.getApplication().getCode();
        }
        if (account.getOauthClient() != null
                && account.getOauthClient().getApplication() != null
                && account.getOauthClient().getApplication().getCode() != null) {
            return account.getOauthClient().getApplication().getCode();
        }
        return "";
    }

    private Optional<IGRPUserEntity> resolveUser(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        return userRepository.findById(subject);
    }

    private Optional<ServiceAccountEntity> resolveServiceAccount(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        try {
            UUID id = UUID.fromString(subject);
            return serviceAccountRepository.findByIdWithRolesAndPermissions(id)
                    .filter(this::isUsableServiceAccount);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private boolean isUsableServiceAccount(ServiceAccountEntity serviceAccount) {
        return serviceAccount != null
                && serviceAccount.isActive()
                && serviceAccount.getOauthClient() != null
                && serviceAccount.getOauthClient().isActive();
    }

    private boolean isSuperAdmin(Optional<IGRPUserEntity> user) {
        if (user.isEmpty() || user.get().getUserRoleAssignments() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return user.get().getUserRoleAssignments().stream()
                .anyMatch(assignment -> isActiveSuperAdminAssignment(assignment, now));
    }

    private boolean isActiveSuperAdminAssignment(UserRoleAssignment assignment, LocalDateTime now) {
        return assignment != null
                && RootUser.isRootRole(assignment.getRole())
                && (assignment.getExpiresAt() == null || assignment.getExpiresAt().isAfter(now));
    }

    private Map<String, Object> standardIdentityClaims(Optional<IGRPUserEntity> user, Set<String> scopes) {
        if (user.isEmpty()) {
            return Collections.emptyMap();
        }

        IGRPUserEntity igrpUser = user.get();
        Map<String, Object> metadata = igrpUser.getMetadata() != null
                ? igrpUser.getMetadata()
                : Collections.emptyMap();

        Map<String, Object> claims = new LinkedHashMap<>();
        if (hasScope(scopes, "profile")) {
            putIfPresent(claims, "name", firstNonBlank(
                    igrpUser.getName(),
                    asString(metadata.get("name"))
            ));
            putIfPresent(claims, CLAIM_GIVEN_NAME, firstNonBlank(
                    asString(metadata.get(CLAIM_GIVEN_NAME))
            ));
            putIfPresent(claims, CLAIM_FAMILY_NAME, firstNonBlank(
                    asString(metadata.get(CLAIM_FAMILY_NAME))
            ));
            putIfPresent(claims, CLAIM_PREFERRED_USERNAME, firstNonBlank(
                    igrpUser.getUsername(),
                    asString(metadata.get(CLAIM_PREFERRED_USERNAME))
            ));
            putIfPresent(claims, CLAIM_PICTURE, firstNonBlank(
                    igrpUser.getPicture(),
                    asString(metadata.get(CLAIM_PICTURE))
            ));
            putIfPresent(claims, CLAIM_LOCALE, firstNonBlank(
                    asString(metadata.get(CLAIM_LOCALE))
            ));
        }

        if (hasScope(scopes, CLAIM_EMAIL)) {
            putIfPresent(claims, CLAIM_EMAIL, firstNonBlank(
                    igrpUser.getEmail(),
                    asString(metadata.get(CLAIM_EMAIL))
            ));

            firstNonNullBoolean(igrpUser.getEmailVerified(), metadata.get(CLAIM_EMAIL_VERIFIED))
                    .ifPresent(emailVerified -> claims.put(CLAIM_EMAIL_VERIFIED, emailVerified));
        }

        if (hasScope(scopes, "phone")) {
            putIfPresent(claims, CLAIM_PHONE_NUMBER, firstNonBlank(
                    igrpUser.getPhoneNumber(),
                    asString(metadata.get(CLAIM_PHONE_NUMBER))
            ));
        }

        if (hasScope(scopes, "nic")) {
            putIfPresent(claims, "nic", firstNonBlank(
                    igrpUser.getNic(),
                    asString(metadata.get("nic"))
            ));
        }

        return claims;
    }

    private void putIfPresent(Map<String, Object> claims, String key, String value) {
        if (value != null && !value.isBlank()) {
            claims.put(key, value);
        }
    }

    private boolean hasScope(Set<String> scopes, String expectedScope) {
        return scopes != null && scopes.contains(expectedScope);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Optional<Boolean> firstNonNullBoolean(Object... values) {
        for (Object value : values) {
            if (value instanceof Boolean bool) {
                return Optional.of(bool);
            }
            if (value instanceof String str && !str.isBlank()) {
                return Optional.of(Boolean.valueOf(str));
            }
        }
        return Optional.empty();
    }
}
