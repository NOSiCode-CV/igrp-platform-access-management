package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
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
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Duration;
import java.time.Instant;
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

    /**
     * @param userAccessTokenTtlSeconds a user's access token lifetime (#33): 5 minutes by default,
     *                                  {@code IGRP_OAUTH_ACCESS_TOKEN_TTL_SECONDS}; a client with a shorter one keeps it
     * @param refreshMaxLifetimeSeconds how long after signing in a refresh is still accepted (#33): the
     *                                  user's session, {@code IGRP_SSO_SESSION_TIMEOUT_SECONDS}, 8 hours by default
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> igrpTokenCustomizer(ClaimsEnrichmentService claimsService,
                                                                         SecurityAuditService auditService,
                                                                         SessionIssuanceService sessionIssuanceService,
                                                                         @Value("${igrp.oauth.access-token-ttl-seconds:300}")
                                                                         long userAccessTokenTtlSeconds,
                                                                         @Value("${igrp.oauth.refresh-max-lifetime-seconds:28800}")
                                                                         long refreshMaxLifetimeSeconds) {
        TokenLifetimes lifetimes = new TokenLifetimes(Duration.ofSeconds(userAccessTokenTtlSeconds),
                Duration.ofSeconds(refreshMaxLifetimeSeconds));
        return context -> {
            // ID_TOKEN branch: the user's profile, the device id, and our session
            // binding id under {@code igrp_session_id} (until sessions go, #33 part 2).
            //
            // The OIDC-standard {@code sid} that Spring AS writes stays as it is: it is
            // base64url(SHA-256(the AM browser session id)), and Spring AS's
            // OidcLogoutAuthenticationProvider compares it with the browser's AM session
            // on /connect/logout. Removing it, or putting our binding id there, makes
            // logout from the browser that signed in fail with a bare 401
            // (OidcLoginFlowIT.logoutFromTheSameBrowser). It carries no authorization
            // data, so the thin-token rule (#33) keeps it on the ID token only.
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
                    // The profile the scopes allow, for the frontend and userinfo (#33), of the
                    // user the access token names.
                    Object accessSubject = accessClaims.get("sub");
                    if (accessSubject != null) {
                        claimsService.buildIdTokenClaims(String.valueOf(accessSubject), context.getAuthorizedScopes())
                                .forEach((key, value) -> context.getClaims().claim(key, value));
                    }
                }
                return;
            }
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                return;
            }

            String internalSub = null;
            String upstreamIdToken = null;
            String ssoLoginId = null;
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
                // R1.6 — which AM sign-in this authorization was granted under.
                // Minted in IgrpOidcUserService and carried on the principal,
                // which Spring AS persists with the authorization; that is why
                // it is readable here, at a token endpoint call that has no
                // browser cookie of its own. Null for a principal from before
                // this field existed, which simply means no key (R1.5).
                if (oauth2Token.getPrincipal() instanceof IgrpOidcUser igrpOidcUser) {
                    ssoLoginId = igrpOidcUser.getSsoLoginId();
                    if (ssoLoginId == null) {
                        LOG.debug("No SSO login id on the principal for sub={}; this session will not "
                                + "take part in a logout cascade", internalSub);
                    }
                }
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
            if (AuthorizationGrantType.REFRESH_TOKEN.equals(context.getAuthorizationGrantType())) {
                lifetimes.checkRefreshAllowed(context.getAuthorization());
            }
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
            context.getClaims().claim("azp", clientId);
            if (!isClientCredentials) {
                lifetimes.capUserAccessToken(context);
            }

            // Phase B — bind the JWT to a server-side session and add sid/device_id claims.
            // Skipped for client_credentials (M2M) where there is no end-user subject.
            String issuanceUserId = parseUserId(internalSub);
            if (!isClientCredentials && issuanceUserId != null) {
                String jti = context.getClaims().build().getId();
                try {
                    SessionIssuanceService.IssuanceBinding binding =
                            sessionIssuanceService.bindAccessToken(context, issuanceUserId, clientId, jti,
                                    upstreamIdToken, ssoLoginId);
                    context.getClaims().claim("sid", binding.sid().toString());
                    context.getClaims().claim("device_id", binding.deviceId());
                } catch (SessionIssuanceService.SessionRefreshRejectedException ex) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                                    ex.getMessage(),
                                    null),
                            ex);
                } catch (RuntimeException ex) {
                    // Anything else that goes wrong while opening the session is
                    // OUR failure, and it must say so.
                    //
                    // Left to propagate, it escaped the authorization-server
                    // filter chain, was forwarded to /error, and the security
                    // chain denied that as anonymous — so the caller received a
                    // bare 401 with no OAuth error body. That is the same shape
                    // as invalid_client, which sent an integration team hunting a
                    // credentials problem while the real cause was a duplicate
                    // key on t_user_session (the ux_session_user_device_active
                    // index, since dropped).
                    //
                    // server_error is the honest code: the request was fine, we
                    // could not complete it. Spring AS renders it as a proper
                    // OAuth error body with a 500, so clients can tell "retry or
                    // escalate" from "fix your credentials". The cause is logged
                    // here because the error returned to the caller is
                    // deliberately generic.
                    LOG.error("Session binding failed during token issuance for user={} client={}; "
                            + "returning server_error", issuanceUserId, clientId, ex);
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                                    "The session could not be established for this token.",
                                    null),
                            ex);
                }
            }

            // Record token issuance into the unified security audit trail. (The
            // former parallel AuthAuditLog write was removed in Phase 1 of the
            // Unified Audit feature — this is now the single source of truth.)
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

    /** A user's access token lifetime and the refresh token's maximum lifetime (#33). */
    record TokenLifetimes(Duration userAccessToken, Duration refreshMax) {

        /** A user's access token lives {@code userAccessToken}, or less when its client says so. */
        void capUserAccessToken(JwtEncodingContext context) {
            Duration clientTtl = context.getRegisteredClient().getTokenSettings().getAccessTokenTimeToLive();
            Duration ttl = clientTtl != null && clientTtl.compareTo(userAccessToken) < 0 ? clientTtl : userAccessToken;
            Instant issuedAt = context.getClaims().build().getIssuedAt();
            context.getClaims().expiresAt((issuedAt != null ? issuedAt : Instant.now()).plus(ttl));
        }

        /**
         * A refresh more than {@code refreshMax} after the sign-in (the authorization code's
         * issue) is refused: the session is over. So is one with no sign-in to count from (a
         * grant without a code): its refresh chain could otherwise last forever.
         */
        void checkRefreshAllowed(OAuth2Authorization authorization) {
            OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization == null ? null
                    : authorization.getToken(OAuth2AuthorizationCode.class);
            Instant signedIn = code == null ? null : code.getToken().getIssuedAt();
            if (signedIn == null || Instant.now().isAfter(signedIn.plus(refreshMax))) {
                throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                        "The sign-in is older than the refresh token's maximum lifetime; sign in again.", null));
            }
        }
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
