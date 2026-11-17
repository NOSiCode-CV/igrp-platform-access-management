package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;

/**
 * Wiring for JWT signing, the registered-client bridge, and iGRP-specific
 * token customization at the authorization server layer.
 */
@Configuration
public class JwtTokenConfig {

    private final KeyUtils keyUtils;
    private final String issuer;
    private final String keyId;

    /**
     * OWASP A02 — optional secondary RSA key for zero-downtime key rotation.
     * When all three secondary properties are set, the secondary key is
     * advertised in the JWKS endpoint alongside the primary key so resource
     * servers can verify tokens signed by either key during the rotation window.
     * See {@link RotatingJWKSource} for the rotation procedure.
     */
    private final String secondaryKeyId;
    private final String secondaryPublicKeyLocation;
    private final String secondaryPrivateKeyLocation;

    public JwtTokenConfig(KeyUtils keyUtils,
                          @Value("${igrp.oauth.issuer:http://localhost:8080}") String issuer,
                          @Value("${igrp.oauth.keys.kid:igrp-oauth-key}") String keyId,
                          @Value("${igrp.oauth.keys.secondary.kid:}") String secondaryKeyId,
                          @Value("${igrp.oauth.keys.secondary.public:}") String secondaryPublicKeyLocation,
                          @Value("${igrp.oauth.keys.secondary.private:}") String secondaryPrivateKeyLocation) {
        this.keyUtils = keyUtils;
        this.issuer = issuer;
        this.keyId = keyId;
        this.secondaryKeyId = secondaryKeyId;
        this.secondaryPublicKeyLocation = secondaryPublicKeyLocation;
        this.secondaryPrivateKeyLocation = secondaryPrivateKeyLocation;
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuthClientJpaRepository repository) {
        return new IgrpRegisteredClientRepository(repository);
    }

    /**
     * OWASP A02 — multi-key {@link JWKSource} that supports zero-downtime key
     * rotation. The primary key signs all new tokens; when a secondary key is
     * configured, both keys are published in the JWKS endpoint so resource
     * servers can validate tokens signed before the rotation.
     *
     * @see RotatingJWKSource
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        RSAKey primaryKey = new RSAKey.Builder(keyUtils.loadPublicKey())
                .privateKey(keyUtils.loadPrivateKey())
                .keyID(keyId)
                .build();

        boolean hasSecondary = !secondaryKeyId.isBlank()
                && !secondaryPublicKeyLocation.isBlank()
                && !secondaryPrivateKeyLocation.isBlank();

        if (hasSecondary) {
            RSAKey secondaryKey = new RSAKey.Builder(
                    keyUtils.loadPublicKey(secondaryPublicKeyLocation))
                    .privateKey(keyUtils.loadPrivateKey(secondaryPrivateKeyLocation))
                    .keyID(secondaryKeyId)
                    .build();
            return new RotatingJWKSource(primaryKey, secondaryKey);
        }

        return new RotatingJWKSource(primaryKey);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JWT decoder configured against the primary public key used for signing.
     * Provided so other components (e.g. custom token-validation utilities)
     * can resolve tokens issued by this authorization server.
     */
    @Bean
    public JwtDecoder internalJwtDecoder(KeyUtils keyUtils) throws Exception {
        return NimbusJwtDecoder.withPublicKey(keyUtils.loadPublicKey()).build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }

    /**
     * @param userAccessTokenTtlSeconds a user's access token lifetime (#33): 5 minutes by default,
     *                                  {@code IGRP_OAUTH_ACCESS_TOKEN_TTL_SECONDS}; a client with a shorter one keeps it
     * @param refreshMaxLifetimeSeconds how long after signing in a refresh is still accepted (#33): the
     *                                  user's session, {@code IGRP_SSO_SESSION_TIMEOUT_SECONDS}, 8 hours by default
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> igrpTokenCustomizer(ClaimsEnrichmentService claimsService,
                                                                         SecurityAuditService auditService,
                                                                         @Value("${igrp.oauth.access-token-ttl-seconds:300}")
                                                                         long userAccessTokenTtlSeconds,
                                                                         @Value("${igrp.oauth.refresh-max-lifetime-seconds:28800}")
                                                                         long refreshMaxLifetimeSeconds) {
        return new IgrpTokenCustomizer(claimsService, auditService,
                Duration.ofSeconds(userAccessTokenTtlSeconds), Duration.ofSeconds(refreshMaxLifetimeSeconds));
    }

}
