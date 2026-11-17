package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
import java.util.UUID;

/**
 * The iGRP claims of every token this authorization server issues (v3-25, #33;
 * spec "Tokens"). Tokens say only who the caller is:
 * <ul>
 *   <li>the ID token: the user's profile ({@link ClaimsEnrichmentService#buildIdTokenClaims}), the
 *       device id and our session binding id ({@code igrp_session_id}, until sessions go);</li>
 *   <li>the access token: the internal subject, the identity claims
 *       ({@link ClaimsEnrichmentService#buildClaims}), {@code azp}, a user's 5-minute lifetime, and
 *       the session binding ({@code sid}, {@code device_id});</li>
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

    private static final Logger LOG = LoggerFactory.getLogger(IgrpTokenCustomizer.class);

    static final String CLAIM_DEVICE_ID = "device_id";
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_SID = "sid";

    private final ClaimsEnrichmentService claimsService;
    private final SecurityAuditService auditService;
    private final SessionIssuanceService sessionIssuanceService;
    private final Duration userAccessTokenTtl;
    private final Duration refreshMaxLifetime;

    /**
     * @param userAccessTokenTtl a user's access token lifetime: 5 minutes by default; a client
     *                           with a shorter one keeps it
     * @param refreshMaxLifetime how long after signing in a refresh is still accepted: the user's
     *                           session, 8 hours by default
     */
    public IgrpTokenCustomizer(ClaimsEnrichmentService claimsService, SecurityAuditService auditService,
                               SessionIssuanceService sessionIssuanceService, Duration userAccessTokenTtl,
                               Duration refreshMaxLifetime) {
        this.claimsService = claimsService;
        this.auditService = auditService;
        this.sessionIssuanceService = sessionIssuanceService;
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

    /** The profile, device id and session binding id of the user the access token names. */
    private void customizeIdToken(JwtEncodingContext context) {
        OAuth2Authorization authorization = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> access = authorization == null ? null
                : authorization.getAccessToken();
        Map<String, Object> accessClaims = access == null ? null : access.getClaims();
        if (accessClaims == null) {
            return;
        }
        copy(accessClaims, CLAIM_SID, context, "igrp_session_id");
        copy(accessClaims, CLAIM_DEVICE_ID, context, CLAIM_DEVICE_ID);
        Object subject = accessClaims.get(CLAIM_SUB);
        if (subject != null) {
            claimsService.buildIdTokenClaims(String.valueOf(subject), context.getAuthorizedScopes())
                    .forEach((key, value) -> context.getClaims().claim(key, value));
        }
    }

    private static void copy(Map<String, Object> from, String name, JwtEncodingContext context, String as) {
        Object value = from.get(name);
        if (value != null) {
            context.getClaims().claim(as, value);
        }
    }

    private void customizeAccessToken(JwtEncodingContext context) {
        String clientId = context.getRegisteredClient().getClientId();
        AuthorizationGrantType grant = context.getAuthorizationGrantType();
        boolean clientCredentials = AuthorizationGrantType.CLIENT_CREDENTIALS.equals(grant);
        Principal principal = principal(context);
        if (AuthorizationGrantType.REFRESH_TOKEN.equals(grant)) {
            checkRefreshAllowed(context.getAuthorization());
        }
        String subject = clientCredentials
                ? claimsService.resolveServiceAccountSubject(clientId).orElse(principal.subject())
                : principal.subject();
        if (subject != null) {
            context.getClaims().subject(subject);
        }
        claimsService.buildClaims(subject, clientId, context.getAuthorizedScopes())
                .forEach((key, value) -> context.getClaims().claim(key, value));
        context.getClaims().claim("azp", clientId);
        if (!clientCredentials) {
            capUserAccessToken(context);
            // Bind the JWT to a server-side session: none for client_credentials (no end user).
            String userId = parseUserId(subject);
            if (userId != null) {
                bindSession(context, userId, clientId, principal);
            }
        }
        audit(grant, clientId, subject);
    }

    /** Who signed in, as the token's subject, and what the logout cascade needs from the sign-in. */
    private record Principal(String subject, String upstreamIdToken, String ssoLoginId) {
    }

    private Principal principal(JwtEncodingContext context) {
        Authentication authentication = context.getPrincipal();
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            return federatedPrincipal(oauth2Token);
        }
        if (authentication != null && !(authentication instanceof OAuth2ClientAuthenticationToken)
                && authentication.getName() != null) {
            return new Principal(authentication.getName(), null, null);
        }
        return new Principal(null, null, null);
    }

    /**
     * A user signed in through a federated IdP: mapped to the internal user, by email first.
     * The upstream id_token is replayed as id_token_hint on the RP-initiated logout cascade
     * (strict IdPs ignore post_logout_redirect_uri without it), and the SSO login id (R1.6,
     * carried on the principal Spring AS persists with the authorization) names the sign-in.
     */
    private Principal federatedPrincipal(OAuth2AuthenticationToken token) {
        OAuth2User user = token.getPrincipal();
        if (user == null) {
            return new Principal(null, null, null);
        }
        Map<String, Object> attributes = user.getAttributes();
        String provider = token.getAuthorizedClientRegistrationId();
        String email = (String) attributes.get("email");
        String subject = email == null ? null : claimsService.mapEmail(provider, email);
        if (subject == null) {
            subject = claimsService.mapSubject(provider, (String) attributes.get(CLAIM_SUB));
        }
        String ssoLoginId = user instanceof IgrpOidcUser igrpOidcUser ? igrpOidcUser.getSsoLoginId() : null;
        if (ssoLoginId == null) {
            LOG.debug("No SSO login id on the principal for sub={}; this session will not "
                    + "take part in a logout cascade", subject);
        }
        String upstreamIdToken = null;
        if (user instanceof OidcUser oidcUser && oidcUser.getIdToken() != null) {
            upstreamIdToken = oidcUser.getIdToken().getTokenValue();
            LOG.debug("Captured upstream id_token ({} chars) for provider={} sub={} to stash on the session row",
                    upstreamIdToken.length(), provider, subject);
        } else {
            LOG.warn("No upstream id_token to capture at issuance: principal type={}. RP-initiated logout "
                            + "cascade will be missing id_token_hint and strict IdPs (Autentika, WSO2 IS) will "
                            + "refuse to honor post_logout_redirect_uri.",
                    user.getClass().getName());
        }
        return new Principal(subject, upstreamIdToken, ssoLoginId);
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

    private void bindSession(JwtEncodingContext context, String userId, String clientId, Principal principal) {
        String jti = context.getClaims().build().getId();
        try {
            SessionIssuanceService.IssuanceBinding binding = sessionIssuanceService.bindAccessToken(context,
                    userId, clientId, jti, principal.upstreamIdToken(), principal.ssoLoginId());
            context.getClaims().claim(CLAIM_SID, binding.sid().toString());
            context.getClaims().claim(CLAIM_DEVICE_ID, binding.deviceId());
        } catch (SessionIssuanceService.SessionRefreshRejectedException ex) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, ex.getMessage(), null), ex);
        } catch (RuntimeException ex) {
            throw sessionBindingFailed(userId, clientId, ex);
        }
    }

    /**
     * Our failure, and it must say so. Left to propagate, it reached /error and came back as a
     * bare 401, the shape of invalid_client. A server_error is the honest code, and the cause
     * is logged here because the error returned is deliberately generic.
     */
    private static OAuth2AuthenticationException sessionBindingFailed(String userId, String clientId,
                                                                      RuntimeException cause) {
        LOG.error("Session binding failed during token issuance for user={} client={}; returning server_error",
                userId, clientId, cause);
        return new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
                "The session could not be established for this token.", null), cause);
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

    private static String parseUserId(String sub) {
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            UUID.fromString(sub);
            return sub;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
