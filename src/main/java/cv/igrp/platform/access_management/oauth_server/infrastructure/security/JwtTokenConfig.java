package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.shared.domain.audit.AuthEventType;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Wiring for JWT signing, the registered-client bridge, and iGRP-specific
 * token customization at the authorization server layer.
 */
@Configuration
public class JwtTokenConfig {

    private final KeyUtils keyUtils;
    private final String issuer;
    private final String keyId;

    public JwtTokenConfig(KeyUtils keyUtils,
                          @Value("${igrp.oauth.issuer:http://localhost:8080}") String issuer,
                          @Value("${igrp.oauth.keys.kid:igrp-oauth-key}") String keyId) {
        this.keyUtils = keyUtils;
        this.issuer = issuer;
        this.keyId = keyId;
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuthClientJpaRepository repository) {
        return new IgrpRegisteredClientRepository(repository);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        RSAPublicKey publicKey = keyUtils.loadPublicKey();
        RSAPrivateKey privateKey = keyUtils.loadPrivateKey();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * JWT decoder configured against the same JWK set used for signing.
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

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> igrpTokenCustomizer(ClaimsEnrichmentService claimsService,
                                                                         AuthAuditService authAuditService,
                                                                         SecurityAuditService auditService,
                                                                         SessionIssuanceService sessionIssuanceService) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String internalSub = null;
            if (context.getPrincipal() instanceof OAuth2AuthenticationToken oauth2Token) {
                Map<String, Object> attributes = oauth2Token.getPrincipal().getAttributes();
                String provider = oauth2Token.getAuthorizedClientRegistrationId();
                String externalUserId = (String) attributes.get("sub");
                internalSub = claimsService.mapSubject(provider, externalUserId);
                if (internalSub != null) {
                    context.getClaims().subject(internalSub);
                }
            } else if (context.getPrincipal() != null
                    && !(context.getPrincipal() instanceof OAuth2ClientAuthenticationToken)) {
                Object name = context.getPrincipal().getName();
                if (name != null) {
                    internalSub = String.valueOf(name);
                }
            }

            String clientId = context.getRegisteredClient().getClientId();
            Map<String, Object> claims = claimsService.buildClaims(internalSub, clientId);
            claims.forEach((key, value) -> context.getClaims().claim(key, value));

            // Phase B — bind the JWT to a server-side session and add sid/device_id claims.
            // Skipped for client_credentials (M2M) where there is no end-user subject.
            boolean isClientCredentials = AuthorizationGrantType.CLIENT_CREDENTIALS
                    .equals(context.getAuthorizationGrantType());
            Integer issuanceUserId = parseUserId(internalSub);
            if (!isClientCredentials && issuanceUserId != null) {
                String jti = context.getClaims().build().getId();
                try {
                    SessionIssuanceService.IssuanceBinding binding =
                            sessionIssuanceService.bindAccessToken(context, issuanceUserId, clientId, jti);
                    context.getClaims().claim("sid", binding.sid().toString());
                    context.getClaims().claim("device_id", binding.deviceId());
                } catch (SessionIssuanceService.SessionRefreshRejectedException ex) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                                    ex.getMessage(),
                                    null),
                            ex);
                }
            }

            String auditSessionId = context.getAuthorization() != null ? context.getAuthorization().getId() : null;
            authAuditService.logEvent(
                    AuthEventType.TOKEN_ISSUED,
                    claimsService.buildTokenIssuedAuditContext(internalSub, clientId, auditSessionId)
            );

            // Record token issuance into the platform security audit trail.
            Map<String, Object> auditContext = new HashMap<>();
            auditContext.put("clientId", clientId);
            auditContext.put("grantType", context.getAuthorizationGrantType().getValue());
            if (internalSub != null) {
                auditContext.put("sub", internalSub);
            }
            auditService.logEvent(
                    cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType.TOKEN_ISSUED,
                    AuditCategory.AUTHENTICATION,
                    auditContext
            );
        };
    }

    private static Integer parseUserId(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
