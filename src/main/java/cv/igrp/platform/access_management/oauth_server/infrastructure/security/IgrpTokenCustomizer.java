package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * The iGRP claims of every token this authorization server issues (v3-25, #33;
 * spec "Tokens"). Tokens say only who the caller is:
 * <ul>
 *   <li>the ID token: the user's profile ({@link ClaimsEnrichmentService#buildIdTokenClaims}) and
 *       the device id;</li>
 *   <li>the access token: the internal subject, the identity claims
 *       ({@link ClaimsEnrichmentService#buildClaims}), {@code azp}, and for a user a 5-minute
 *       lifetime and the {@code device_id}. There is no session and no iGRP {@code sid}: a user is
 *       signed in while they hold an active authorization ({@link UserAuthorizations});</li>
 *   <li>a token exchange (RFC 8693, v3-20): the user, the one target service as {@code aud} and
 *       the chain of actors as {@code act}, checked and written by {@link TokenExchangeClaimsService};</li>
 *   <li>a refresh after the refresh token's maximum lifetime is refused.</li>
 * </ul>
 *
 * <p>The OIDC-standard {@code sid} that Spring AS writes on the ID token stays as it is: it
 * is base64url(SHA-256(the AM browser session id)), and Spring AS's
 * OidcLogoutAuthenticationProvider compares it with the browser's AM session on
 * /connect/logout. Removing it, or putting our binding id there, makes logout from the
 * browser that signed in fail with a bare 401 (OidcLoginFlowIT.logoutFromTheSameBrowser).
 */
public final class IgrpTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    static final String CLAIM_DEVICE_ID = "device_id";
    private static final String CLAIM_SUB = "sub";

    private final ClaimsEnrichmentService claimsService;
    private final SecurityAuditService auditService;
    private final TokenExchangeClaimsService tokenExchange;
    private final Duration userAccessTokenTtl;
    private final Duration refreshMaxLifetime;

    /**
     * @param userAccessTokenTtl a user's access token lifetime: 5 minutes by default; a client
     *                           with a shorter one keeps it
     * @param refreshMaxLifetime how long after signing in a refresh is still accepted: the user's
     *                           session, 8 hours by default
     */
    public IgrpTokenCustomizer(ClaimsEnrichmentService claimsService, SecurityAuditService auditService,
                               TokenExchangeClaimsService tokenExchange,
                               Duration userAccessTokenTtl, Duration refreshMaxLifetime) {
        this.claimsService = claimsService;
        this.auditService = auditService;
        this.tokenExchange = tokenExchange;
        this.userAccessTokenTtl = userAccessTokenTtl;
        this.refreshMaxLifetime = refreshMaxLifetime;
    }

    @Override
    public void customize(JwtEncodingContext context) {
        if ("id_token".equals(context.getTokenType().getValue())) {
            customizeIdToken(context);
        } else if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            customizeAccessToken(context);
        }
    }

    /** The profile and device id of the user the access token names. */
    private void customizeIdToken(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization == null ? null
                : authorization.getAccessToken();
        Map<String, Object> accessClaims = access == null ? null : access.getClaims();
        if (accessClaims == null) {
            return;
        }
        Object deviceId = accessClaims.get(CLAIM_DEVICE_ID);
        if (deviceId != null) {
            context.getClaims().claim(CLAIM_DEVICE_ID, deviceId);
        }
        Object subject = accessClaims.get(CLAIM_SUB);
        if (subject != null) {
            claimsService.buildIdTokenClaims(String.valueOf(subject), context.getAuthorizedScopes())
                    .forEach((key, value) -> context.getClaims().claim(key, value));
        }
    }

    private void customizeAccessToken(JwtEncodingContext context) {
        String clientId = context.getRegisteredClient().getClientId();
        AuthorizationGrantType grant = context.getAuthorizationGrantType();
        if (AuthorizationGrantType.TOKEN_EXCHANGE.equals(grant)) {
            customizeExchangedToken(context, clientId);
            return;
        }
        boolean clientCredentials = AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grant);
        String principal = principal(context);
        if (AuthorizationGrantType.REFRESH_TOKEN.equals(grant)) {
            checkRefreshAllowed(context.getAuthorization());
        }
        String subject = clientCredentials
                ? claimsService.resolveServiceAccountSubject(clientId).orElse(principal)
                : principal;
        if (subject != null) {
            context.getClaims().subject(subject);
        }
        claimsService.buildClaims(subject, clientId, context.getAuthorizedScopes())
                .forEach((key, value) -> context.getClaims().claim(key, value));
        context.getClaims().claim("azp", clientId);
        if (!clientCredentials) {
            capUserAccessToken(context);
            context.getClaims().claim(CLAIM_DEVICE_ID, deviceId(context, clientId));
        }
        audit(grant, clientId, subject);
    }

    /**
     * An exchanged token: the policy and {@code sub}/{@code aud}/{@code exp}/{@code act} come from
     * {@link TokenExchangeClaimsService}; the user's identity claims and {@code azp} are those of
     * any user token. Its lifetime is already no longer than the subject token's.
     */
    private void customizeExchangedToken(JwtEncodingContext context, String clientId) {
        String subject = tokenExchange.customize(context);
        claimsService.buildClaims(subject, clientId, context.getAuthorizedScopes())
                .forEach((key, value) -> context.getClaims().claim(key, value));
        context.getClaims().claim("azp", clientId);
        JwtClaimsSet built = context.getClaims().build();
        Map<String, Object> auditContext = new HashMap<>();
        auditContext.put("clientId", clientId);
        auditContext.put("grantType", AuthorizationGrantType.TOKEN_EXCHANGE.getValue());
        auditContext.put(CLAIM_SUB, subject);
        auditContext.put("audience", built.getAudience());
        auditContext.put(TokenExchangeClaimsService.CLAIM_ACT, built.getClaim(TokenExchangeClaimsService.CLAIM_ACT));
        auditService.logEvent(AuditEventType.TOKEN_ISSUED, AuditCategory.AUTHENTICATION, auditContext);
    }

    /** Who signed in, as the token's subject. */
    private String principal(JwtEncodingContext context) {
        Authentication authentication = context.getPrincipal();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            return federatedSubject(oauth2Token);
        }
        if (authentication != null && !(authentication instanceof OAuth2ClientAuthenticationToken)) {
            return authentication.getName();
        }
        return null;
    }

    /** A user signed in through a federated IdP: mapped to the internal user, by email first. */
    private String federatedSubject(OAuth2AuthenticationToken token) {
        OAuth2User user = token.getPrincipal();
        if (user == null) {
            return null;
        }
        Map<String, Object> attributes = user.getAttributes();
        String provider = token.getAuthorizedClientRegistrationId();
        String email = (String) attributes.get("email");
        String subject = email == null ? null : claimsService.mapEmail(provider, email);
        return subject != null ? subject : claimsService.mapSubject(provider, (String) attributes.get(CLAIM_SUB));
    }

    /** A refresh keeps the device id of the token it replaces; a sign-in takes the request's. */
    private static String deviceId(JwtEncodingContext context, String clientId) {
        OAuth2Authorization authorization = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> previous = authorization == null ? null
                : authorization.getAccessToken();
        Map<String, Object> claims = previous == null ? null : previous.getClaims();
        Object deviceId = claims == null ? null : claims.get(CLAIM_DEVICE_ID);
        return deviceId != null ? deviceId.toString() : DeviceIds.current(clientId);
    }

    /** A user's access token lives {@code userAccessTokenTtl}, or less when its client says so. */
    private void capUserAccessToken(JwtEncodingContext context) {
        Duration clientTtl = context.getRegisteredClient().getTokenSettings().getAccessTokenTimeToLive();
        Duration ttl = clientTtl.compareTo(userAccessTokenTtl) < 0 ? clientTtl : userAccessTokenTtl;
        Instant issuedAt = context.getClaims().build().getIssuedAt();
        context.getClaims().expiresAt((issuedAt != null ? issuedAt : Instant.now()).plus(ttl));
    }

    /**
     * A refresh more than {@code refreshMaxLifetime} after the sign-in (the authorization
     * code's issue) is refused: the session is over. So is one with no sign-in to count from (a
     * grant without a code): its refresh chain could otherwise last forever.
     */
    private void checkRefreshAllowed(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AuthorizationCode> code = authorization == null ? null
                : authorization.getToken(OAuth2AuthorizationCode.class);
        Instant signedIn = code == null ? null : code.getToken().getIssuedAt();
        if (signedIn == null || Instant.now().isAfter(signedIn.plus(refreshMaxLifetime))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
                    "The sign-in is older than the refresh token's maximum lifetime; sign in again.", null));
        }
    }

    /** Token issuance in the unified security audit trail. */
    private void audit(AuthorizationGrantType grant, String clientId, String subject) {
        Map<String, Object> auditContext = new HashMap<>();
        auditContext.put("clientId", clientId);
        auditContext.put("grantType", grant == null ? null : grant.getValue());
        if (subject != null) {
            auditContext.put(CLAIM_SUB, subject);
        }
        auditService.logEvent(AuditEventType.TOKEN_ISSUED, AuditCategory.AUTHENTICATION, auditContext);
    }
}
