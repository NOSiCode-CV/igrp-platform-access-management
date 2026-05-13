package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.shared.domain.audit.AuthAuditContext;
import cv.igrp.platform.access_management.shared.domain.audit.IdentifierType;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the iGRP-specific claims injected into issued JWTs.
 *
 * <p>Values are resolved dynamically from the platform data model:
 * <ul>
 *     <li>{@code sub} — mapped to the internal {@link IGRPUserEntity} id
 *         when the caller authenticated through a federated provider.</li>
 *     <li>{@code selectedRole} — the user's active role code.</li>
 *     <li>{@code org} — the user's owning application/resource (via client).</li>
 *     <li>{@code permissions} — flattened permission codes for the active role.</li>
 *     <li>{@code resource_access} — Keycloak-compatible per-client role map.</li>
 *     <li>Standard identity claims — {@code name}, {@code given_name},
 *         {@code family_name}, {@code preferred_username}, {@code email},
 *         {@code email_verified}, {@code picture}, {@code phone_number},
 *         {@code locale}.</li>
 *     <li>{@code metadata} — optional app-specific extension claims.</li>
 * </ul>
 */
@Service
public class ClaimsEnrichmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimsEnrichmentService.class);
    private static final Set<String> RESERVED_METADATA_KEYS = Set.of(
            "name",
            "given_name",
            "family_name",
            "preferred_username",
            "email",
            "email_verified",
            "picture",
            "phone_number",
            "locale",
            "nic"
    );

    private final OAuthClientJpaRepository oauthClientRepository;
    private final UserIdentityJpaRepository userIdentityRepository;
    private final IGRPUserEntityRepository userRepository;

    public ClaimsEnrichmentService(OAuthClientJpaRepository oauthClientRepository,
                                   UserIdentityJpaRepository userIdentityRepository,
                                   IGRPUserEntityRepository userRepository) {
        this.oauthClientRepository = oauthClientRepository;
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
        return userIdentityRepository
                .findByProviderAndUserId(provider, externalUserId)
                .map(UserIdentityEntity::getUser)
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

    /**
     * Build the custom claim map for the given subject / client id pair.
     * Missing data is rendered as empty values rather than thrown exceptions
     * so the token issuance pipeline stays robust.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buildClaims(String subject, String clientId, Set<String> grantedScopes) {
        Map<String, Object> claims = new LinkedHashMap<>();
        Set<String> scopes = grantedScopes != null ? new HashSet<>(grantedScopes) : Collections.emptySet();

        Optional<OAuthClientEntity> client = clientId == null
                ? Optional.empty()
                : oauthClientRepository.findByClientId(clientId);

        Optional<IGRPUserEntity> user = resolveUser(subject);

        claims.put("selectedRole", selectedRole(user));
        claims.put("org", selectedOrg(client));
        claims.put("permissions", permissions(user));
        claims.put("resource_access", resourceAccess(clientId, user));
        claims.putAll(standardIdentityClaims(user, scopes));

        Map<String, Object> metadata = filteredMetadata(user);
        if (!metadata.isEmpty()) {
            claims.put("metadata", metadata);
        }

        LOGGER.debug("Enriched claims for subject={} clientId={} -> keys={}",
                subject, clientId, claims.keySet());
        return claims;
    }

    private Optional<IGRPUserEntity> resolveUser(String subject) {
        if (subject == null) {
            return Optional.empty();
        }
        return userRepository.findById(subject);
    }

    private String selectedRole(Optional<IGRPUserEntity> user) {
        return user.map(IGRPUserEntity::getActiveRole)
                .map(RoleEntity::getCode)
                .orElse("");
    }

    private String selectedOrg(Optional<OAuthClientEntity> client) {
        return client.map(OAuthClientEntity::getApplication)
                .map(ApplicationEntity::getCode)
                .orElse("");
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
            putIfPresent(claims, "given_name", firstNonBlank(
                    asString(metadata.get("given_name"))
            ));
            putIfPresent(claims, "family_name", firstNonBlank(
                    asString(metadata.get("family_name"))
            ));
            putIfPresent(claims, "preferred_username", firstNonBlank(
                    igrpUser.getUsername(),
                    asString(metadata.get("preferred_username"))
            ));
            putIfPresent(claims, "picture", firstNonBlank(
                    igrpUser.getPicture(),
                    asString(metadata.get("picture"))
            ));
            putIfPresent(claims, "locale", firstNonBlank(
                    asString(metadata.get("locale"))
            ));
        }

        if (hasScope(scopes, "email")) {
            putIfPresent(claims, "email", firstNonBlank(
                    igrpUser.getEmail(),
                    asString(metadata.get("email"))
            ));

            Boolean emailVerified = firstNonNullBoolean(
                    igrpUser.getEmailVerified(),
                    metadata.get("email_verified")
            );
            if (emailVerified != null) {
                claims.put("email_verified", emailVerified);
            }
        }

        if (hasScope(scopes, "phone")) {
            putIfPresent(claims, "phone_number", firstNonBlank(
                    igrpUser.getPhoneNumber(),
                    asString(metadata.get("phone_number"))
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

    private Map<String, Object> filteredMetadata(Optional<IGRPUserEntity> user) {
        if (user.isEmpty() || user.get().getMetadata() == null || user.get().getMetadata().isEmpty()) {
            return Collections.emptyMap();
        }

        return user.get().getMetadata().entrySet().stream()
                .filter(entry -> !RESERVED_METADATA_KEYS.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    /**
     * Build a shared auth-audit context for tokens issued by the local
     * authorization server. Uses the resolved internal user plus the owning
     * application/client metadata instead of relying on transient browser
     * request state.
     */
    @Transactional(readOnly = true)
    public AuthAuditContext buildTokenIssuedAuditContext(String subject, String clientId, String sessionId) {
        Optional<OAuthClientEntity> client = clientId == null
                ? Optional.empty()
                : oauthClientRepository.findByClientId(clientId);

        Optional<IGRPUserEntity> user = resolveUser(subject);
        IdentifierType identifierType = IdentifierType.UNKNOWN;
        String identifierValue = null;

        if (user.isPresent()) {
            IGRPUserEntity igrpUser = user.get();
            if (igrpUser.getPhoneNumber() != null && !igrpUser.getPhoneNumber().isBlank()) {
                identifierType = IdentifierType.CMDCV;
                identifierValue = igrpUser.getPhoneNumber();
            } else if (igrpUser.getEmail() != null && !igrpUser.getEmail().isBlank()) {
                identifierType = IdentifierType.EMAIL;
                identifierValue = igrpUser.getEmail();
            } else if (igrpUser.getNic() != null && !igrpUser.getNic().isBlank()) {
                identifierType = IdentifierType.CNI;
                identifierValue = igrpUser.getNic();
            }
        }

        String applicationCode = client.map(OAuthClientEntity::getApplication)
                .map(ApplicationEntity::getCode)
                .filter(code -> code != null && !code.isBlank())
                .orElse(clientId);

        return new AuthAuditContext(
                identifierType,
                identifierValue,
                subject,
                applicationCode,
                sessionId,
                null
        );
    }

    private Set<String> permissions(Optional<IGRPUserEntity> user) {
        if (user.isEmpty() || user.get().getActiveRole() == null) {
            return Collections.emptySet();
        }
        RoleEntity role = user.get().getActiveRole();
        Set<PermissionEntity> perms = role.getPermissions();
        if (perms == null) {
            return Collections.emptySet();
        }
        return perms.stream()
                .map(PermissionEntity::getName)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
    }

    private Map<String, Object> resourceAccess(String clientId, Optional<IGRPUserEntity> user) {
        if (clientId == null || user.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> roles = user.get().getRoles().stream()
                .map(RoleEntity::getCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toSet());
        if (roles.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> perClient = new HashMap<>();
        perClient.put("roles", roles);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(clientId, perClient);
        return out;
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

    private Boolean firstNonNullBoolean(Object... values) {
        for (Object value : values) {
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String str && !str.isBlank()) {
                return Boolean.valueOf(str);
            }
        }
        return null;
    }
}
