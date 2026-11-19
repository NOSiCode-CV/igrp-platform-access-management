package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AM's policy for the RFC 8693 token exchange (v3-20, #28; spec "Token exchange and agents"),
 * applied by {@link IgrpTokenCustomizer} before Spring persists the exchanged token. Spring
 * remains responsible for the protocol, client authentication, the subject token being stored
 * and active (a logout or kill deletes it, so the exchange fails), {@code may_act} and the
 * response.
 *
 * <p>The exchanged token is thin like every other token (#33, spec "Tokens"): {@code sub} the
 * user, {@code aud} the one target service, {@code act} the chain of actors, and an expiry no
 * later than any input token's or the target client's lifetime. It carries no permission, role
 * or organisation: the target service reads the user's permission set for its own application.
 * {@code act} is never used for authorization; it is recorded in audit.
 */
@Service
public class TokenExchangeClaimsService {

    static final String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    static final String CLAIM_ACT = "act";
    private static final String CLAIM_SUB = "sub";
    private static final String CLAIM_ISS = "iss";
    private static final String INVALID_GRANT = "invalid_grant";
    private static final String INVALID_TARGET = "invalid_target";
    private static final String INVALID_REQUEST = "invalid_request";

    private final TokenExchangeProperties properties;
    private final OAuthClientJpaRepository clients;
    private final ServiceAccountJpaRepository accounts;
    private final IGRPUserEntityRepository users;
    private final OAuth2AuthorizationService authorizations;

    public TokenExchangeClaimsService(TokenExchangeProperties properties, OAuthClientJpaRepository clients,
                                      ServiceAccountJpaRepository accounts, IGRPUserEntityRepository users,
                                      OAuth2AuthorizationService authorizations) {
        this.properties = properties;
        this.clients = clients;
        this.accounts = accounts;
        this.users = users;
        this.authorizations = authorizations;
    }

    /**
     * Checks the exchange and writes {@code sub}, {@code aud}, {@code exp}, {@code act} and the
     * subject's {@code device_id}.
     *
     * @return the user the exchanged token names
     * @throws OAuth2AuthenticationException with the RFC 8693 / RFC 6749 error code when refused
     */
    @Transactional(readOnly = true)
    public String customize(JwtEncodingContext context) {
        OAuth2TokenExchangeAuthenticationToken request = context.getAuthorizationGrant();
        require(request != null, INVALID_REQUEST, "Not a token exchange");
        String caller = context.getRegisteredClient().getClientId();
        OAuthClientEntity sourceClient = activeClient(caller);
        require(sourceClient.getGrantTypes().contains(AuthorizationGrantType.TOKEN_EXCHANGE.getValue()),
                "unauthorized_client", "Client is not enabled for token exchange");
        require(request.getAudiences().size() == 1 && request.getResources().isEmpty(),
                INVALID_TARGET, "Exactly one audience and no resource are supported");
        String audience = request.getAudiences().iterator().next();
        OAuthClientEntity target = activeClient(audience);
        ApplicationEntity application = target.getApplication();
        require(application != null && application.getId() != null && application.getStatus() == Status.ACTIVE,
                INVALID_TARGET, "Audience must belong to an active application");
        require(ACCESS_TOKEN.equals(request.getSubjectTokenType())
                        && ACCESS_TOKEN.equals(request.getRequestedTokenType()),
                INVALID_REQUEST, "Only access tokens are supported");

        OAuth2Authorization subject = context.getAuthorization();
        OAuth2Authorization.Token<OAuth2AccessToken> subjectToken = activeAccessToken(subject);
        require(subjectToken != null, INVALID_GRANT, "Subject token is not active");
        Map<String, Object> original = subjectToken.getClaims();
        require(original != null && original.get(CLAIM_SUB) instanceof String,
                INVALID_GRANT, "Subject token has no user identity");
        String userId = (String) original.get(CLAIM_SUB);
        String issuer = context.getAuthorizationServerContext().getIssuer();
        require(sameIssuer(issuer, original), INVALID_GRANT, "Subject issuer is not accepted");
        require(users.findById(userId)
                        .filter(u -> u.getStatus() == Status.ACTIVE || u.getStatus() == Status.TEMPORARY)
                        .isPresent(),
                INVALID_GRANT, "Subject user is unavailable");

        Set<String> scopes = context.getAuthorizedScopes();
        require(subject.getAuthorizedScopes().containsAll(scopes)
                        && sourceClient.getScopes().containsAll(scopes) && target.getScopes().containsAll(scopes),
                "invalid_scope", "Scopes must be permitted by the subject, caller and target");

        Instant expiry = context.getClaims().build().getExpiresAt();
        require(expiry != null && target.getAccessTokenTtl() > 0, INVALID_TARGET, "Invalid target token lifetime");
        expiry = earlier(expiry, subjectToken.getToken().getExpiresAt());
        expiry = earlier(expiry, Instant.now().plusSeconds(target.getAccessTokenTtl()));

        String actorId = caller;
        String actorToken = request.getActorToken();
        if (actorToken != null) {
            OAuth2Authorization.Token<OAuth2AccessToken> actor = actor(actorToken, request.getActorTokenType(),
                    sourceClient);
            actorId = actorAccount(actor, caller, issuer);
            expiry = earlier(expiry, actor.getToken().getExpiresAt());
        } else {
            require(request.getActorTokenType() == null, INVALID_REQUEST, "Unexpected actor_token_type");
        }
        require(expiry.isAfter(Instant.now()), INVALID_GRANT, "Exchange lifetime exhausted");

        Map<String, Object> actorChain = new LinkedHashMap<>();
        actorChain.put(CLAIM_SUB, actorId);
        actorChain.put(CLAIM_ISS, issuer);
        if (original.containsKey(CLAIM_ACT)) {
            actorChain.put(CLAIM_ACT, copyActorChain(original.get(CLAIM_ACT), 1));
        }
        context.getClaims().subject(userId).audience(List.of(audience)).expiresAt(expiry)
                .claim(CLAIM_ACT, actorChain);
        if (original.get(IgrpTokenCustomizer.CLAIM_DEVICE_ID) instanceof String deviceId) {
            context.getClaims().claim(IgrpTokenCustomizer.CLAIM_DEVICE_ID, deviceId);
        }
        return userId;
    }

    /** The actor token: an active client_credentials token of the caller itself. */
    private OAuth2Authorization.Token<OAuth2AccessToken> actor(String actorToken, String actorTokenType,
                                                               OAuthClientEntity caller) {
        require(ACCESS_TOKEN.equals(actorTokenType), INVALID_REQUEST, "actor_token_type must be access_token");
        OAuth2Authorization actor = authorizations.findByToken(actorToken, OAuth2TokenType.ACCESS_TOKEN);
        OAuth2Authorization.Token<OAuth2AccessToken> token = activeAccessToken(actor);
        require(token != null && actor.getRegisteredClientId().equals(caller.getId().toString())
                        && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(actor.getAuthorizationGrantType()),
                INVALID_GRANT, "Actor must be an active client_credentials token of the caller");
        return token;
    }

    /** The authorization's access token when it is still active, else null. */
    private static OAuth2Authorization.Token<OAuth2AccessToken> activeAccessToken(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2AccessToken> token = authorization == null ? null
                : authorization.getAccessToken();
        return token != null && token.isActive() ? token : null;
    }

    /** The caller's service account, which the actor token must name. */
    private String actorAccount(OAuth2Authorization.Token<OAuth2AccessToken> actor, String caller, String issuer) {
        var account = accounts.findByOauthClient_ClientId(caller)
                .filter(a -> a.isActive() && a.getOauthClient().isActive())
                .orElseThrow(() -> error(INVALID_GRANT, "Actor service account is unavailable"));
        String accountId = account.getId().toString();
        Map<String, Object> claims = actor.getClaims();
        require(claims != null && accountId.equals(claims.get(CLAIM_SUB)) && sameIssuer(issuer, claims),
                INVALID_GRANT, "Actor identity does not match the caller service account");
        return accountId;
    }

    private OAuthClientEntity activeClient(String clientId) {
        return clients.findByClientId(clientId).filter(OAuthClientEntity::isActive)
                .orElseThrow(() -> error(INVALID_TARGET, "Client or audience is unavailable"));
    }

    private Map<String, Object> copyActorChain(Object value, int depth) {
        require(depth < properties.getMaxActorDepth(), INVALID_REQUEST, "Actor chain is too deep");
        require(value instanceof Map<?, ?>, INVALID_GRANT, "Malformed actor chain");
        Map<?, ?> actor = (Map<?, ?>) value;
        require(actor.get(CLAIM_SUB) instanceof String sub && !sub.isBlank(),
                INVALID_GRANT, "Malformed actor identity");
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put(CLAIM_SUB, actor.get(CLAIM_SUB));
        if (actor.get(CLAIM_ISS) instanceof String iss) {
            copy.put(CLAIM_ISS, iss);
        }
        if (actor.containsKey(CLAIM_ACT)) {
            copy.put(CLAIM_ACT, copyActorChain(actor.get(CLAIM_ACT), depth + 1));
        }
        return copy;
    }

    /** A stored token's {@code iss} comes back from the authorization store as a URL, not a string. */
    private static boolean sameIssuer(String issuer, Map<String, Object> claims) {
        Object iss = claims.get(CLAIM_ISS);
        return iss != null && issuer.equals(iss.toString());
    }

    private static Instant earlier(Instant first, Instant second) {
        require(second != null, INVALID_GRANT, "Input token must expire");
        return first.isBefore(second) ? first : second;
    }

    private static void require(boolean condition, String code, String description) {
        if (!condition) {
            throw error(code, description);
        }
    }

    private static OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
