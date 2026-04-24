package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.UserIdentityEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom {@link OidcUserService} that maps an external OpenID subject to an
 * internal {@link IGRPUserEntity}, provisioning one on the first login and
 * stamping federated attributes onto the user's metadata column.
 */
@Service
public class IgrpOidcUserService extends OidcUserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IgrpOidcUserService.class);

    private final UserIdentityJpaRepository userIdentityRepository;
    private final IGRPUserEntityRepository userRepository;

    public IgrpOidcUserService(UserIdentityJpaRepository userIdentityRepository,
                               IGRPUserEntityRepository userRepository) {
        this.userIdentityRepository = userIdentityRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidcUser = super.loadUser(request);

        String provider = request.getClientRegistration().getRegistrationId();
        String externalUserId = oidcUser.getSubject();
        Map<String, Object> attributes = oidcUser.getAttributes();

        Optional<UserIdentityEntity> existing = userIdentityRepository
                .findByProviderAndUserId(provider, externalUserId);

        IGRPUserEntity user = existing
                .map(UserIdentityEntity::getUser)
                .orElseGet(() -> provisionUser(provider, externalUserId, attributes));

        updateUserMetadataFromClaims(user, attributes);
        userRepository.save(user);

        LOGGER.debug("Federated OIDC login mapped: provider={} sub={} -> internalId={}",
                provider, externalUserId, user.getId());

        return new DefaultOidcUser(new ArrayList<>(), request.getIdToken(), oidcUser.getUserInfo());
    }

    private IGRPUserEntity provisionUser(String provider, String externalUserId, Map<String, Object> attributes) {
        String preferredUsername = (String) attributes.getOrDefault("preferred_username", externalUserId);
        String email = (String) attributes.get("email");
        String name = (String) attributes.getOrDefault("name", preferredUsername);

        IGRPUserEntity user = userRepository.findByExternalId(externalUserId)
                .orElseGet(IGRPUserEntity::new);
        if (user.getId() == null) {
            user.setExternalId(externalUserId);
            user.setUsername(preferredUsername);
            user.setEmail(email);
            user.setName(name);
            user.setStatus(Status.ACTIVE);
            user.setEmailVerified(Boolean.TRUE.equals(attributes.get("email_verified")));
            user = userRepository.save(user);
            LOGGER.info("Provisioned new IGRP user from provider={} sub={}", provider, externalUserId);
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

    private void updateUserMetadataFromClaims(IGRPUserEntity user, Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        Map<String, Object> metadata = user.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
        }
        // Standard OIDC claims to lift into user metadata; omit sub to avoid duplication.
        for (String key : new String[]{"given_name", "family_name", "preferred_username", "email",
                "email_verified", "locale", "picture", "phone_number"}) {
            Object value = attributes.get(key);
            if (value != null) {
                metadata.put(key, value);
            }
        }
        user.setMetadata(metadata);
    }
}
