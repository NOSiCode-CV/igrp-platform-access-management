package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.shared.domain.audit.AuthEventType;
import cv.igrp.platform.access_management.shared.infrastructure.service.AuthAuditService;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Wiring for JWT signing, the registered-client bridge, and iGRP-specific
 * token customization at the authorization server layer.
 */
@Configuration
public class JwtTokenConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JwtTokenConfig.class);

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

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> igrpTokenCustomizer(ClaimsEnrichmentService claimsService,
                                                                         AuthAuditService authAuditService,
                                                                         SecurityAuditService auditService,
                                                                         SessionIssuanceService sessionIssuanceService) {
        return context -> {
            // ID_TOKEN branch — copy our SessionEntity binding id and device id
            // from the already-issued access token onto the id_token under
            // {@code igrp_session_id}, NOT under the OIDC-standard {@code sid}
            // claim.
            //
            // Why a custom claim instead of the standard `sid`:
            //
            // Spring AS's {@code OidcLogoutAuthenticationProvider} (in
            // spring-security-oauth2-authorization-server 1.5.x) compares the
            // id_token's `sid` claim against {@code base64url(SHA-256(JSESSIONID))}
            // — its own representation of "the current servlet session". When
            // the values disagree it throws OAuth2AuthenticationException
            // ("OpenID Connect 1.0 Logout Request Parameter: sid"), which
            // ExceptionTranslationFilter resolves to a bare 401 Bearer because
            // the auto-installed BearerTokenAuthenticationEntryPoint (MediaType.ALL
            // matcher) wins the entry-point race. The browser-initiated POST
            // /connect/logout then 401s on every logged-in user, while
            // unauthenticated cURL (no JSESSIONID) succeeds because the
            // provider's null-sessionId guard skips the check.
            //
            // Our `binding.sid()` is the SessionEntity UUID — never equal to
            // Spring's hashed JSESSIONID by construction. Setting `sid` to it
            // breaks RP-initiated logout 100% of the time for browsers and
            // hides the failure on cURL.
            //
            // Resolution: keep our binding id in a separate claim
            // ({@code igrp_session_id}). Spring AS sees no `sid` on the id_token,
            // skips its check, and the logout request flows through to our
            // SessionLogoutHandler — which now reads {@code igrp_session_id}
            // to find the SessionEntity (see SessionLogoutHandler.extractSid).
            //
            // Note: we still record `sid` on the ACCESS token via the binding
            // path below (line 234), because SessionEnforcementFilter resolves
            // it from the access token JWT and never trips Spring AS's logout
            // provider check (the provider only reads the id_token).
            if ("id_token".equals(context.getTokenType().getValue())) {
                OAuth2Authorization auth = context.getAuthorization();
                if (auth != null && auth.getAccessToken() != null
                        && auth.getAccessToken().getClaims() != null) {
                    Map<String, Object> accessClaims = auth.getAccessToken().getClaims();
                    Object sidClaim = accessClaims.get("sid");
                    if (sidClaim != null) {
                        context.getClaims().claim("igrp_session_id", sidClaim);
                    }
                    Object deviceClaim = accessClaims.get("device_id");
                    if (deviceClaim != null) {
                        context.getClaims().claim("device_id", deviceClaim);
                    }
                    Object superAdminClaim = accessClaims.get("is_super_admin");
                    if (superAdminClaim != null) {
                        context.getClaims().claim("is_super_admin", superAdminClaim);
                    }
                }
                return;
            }
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String internalSub = null;
            String upstreamIdToken = null;
            if (context.getPrincipal() instanceof OAuth2AuthenticationToken oauth2Token) {
                Map<String, Object> attributes = oauth2Token.getPrincipal().getAttributes();
                String provider = oauth2Token.getAuthorizedClientRegistrationId();

                String userEmail = (String) attributes.get("email");
                if (userEmail != null) {
                    internalSub = claimsService.mapEmail(provider, userEmail);
                }
                if (internalSub == null) {
                    String externalUserId = (String) attributes.get("sub");
                    internalSub = claimsService.mapSubject(provider, externalUserId);
                }
                // Capture the upstream IdP's original id_token so we can replay
                // it as id_token_hint on the RP-initiated logout cascade. WSO2 IS
                // (and other strict IdPs) ignore post_logout_redirect_uri when
                // id_token_hint is missing; capturing it here is the only point
                // in the flow where the upstream OidcUser principal is still
                // attached to the token-issuance context.
                if (oauth2Token.getPrincipal() instanceof OidcUser oidcUser
                        && oidcUser.getIdToken() != null) {
                    upstreamIdToken = oidcUser.getIdToken().getTokenValue();
                    LOG.debug("Captured upstream id_token ({} chars) for provider={} sub={} to stash on the session row",
                            upstreamIdToken.length(), provider, internalSub);
                } else {
                    LOG.warn("No upstream id_token to capture at issuance: principal type={}, "
                            + "OidcUser idToken null? = {}. RP-initiated logout cascade will "
                            + "be missing id_token_hint and strict IdPs (Autentika, WSO2 IS) "
                            + "will refuse to honor post_logout_redirect_uri.",
                            oauth2Token.getPrincipal().getClass().getName(),
                            !(oauth2Token.getPrincipal() instanceof OidcUser));
                }
            } else if (context.getPrincipal() != null
                    && !(context.getPrincipal() instanceof OAuth2ClientAuthenticationToken)) {
                Object name = context.getPrincipal().getName();
                if (name != null) {
                    internalSub = String.valueOf(name);
                }
            }

            String clientId = context.getRegisteredClient().getClientId();
            boolean isClientCredentials = AuthorizationGrantType.CLIENT_CREDENTIALS
                    .equals(context.getAuthorizationGrantType());
            if (isClientCredentials) {
                internalSub = claimsService.resolveServiceAccountSubject(clientId)
                        .orElse(internalSub);
            }
            if (internalSub != null) {
                context.getClaims().subject(internalSub);
            }
            Map<String, Object> claims = claimsService.buildClaims(
                    internalSub,
                    clientId,
                    context.getAuthorizedScopes()
            );
            claims.forEach((key, value) -> context.getClaims().claim(key, value));

            // Phase B — bind the JWT to a server-side session and add sid/device_id claims.
            // Skipped for client_credentials (M2M) where there is no end-user subject.
            String issuanceUserId = parseUserId(internalSub);
            if (!isClientCredentials && issuanceUserId != null) {
                String jti = context.getClaims().build().getId();
                try {
                    SessionIssuanceService.IssuanceBinding binding =
                            sessionIssuanceService.bindAccessToken(context, issuanceUserId, clientId, jti, upstreamIdToken);
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

            // Record token issuance into the platform security audit trail
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

    private static String parseUserId(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            java.util.UUID.fromString(sub);
            return sub;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
