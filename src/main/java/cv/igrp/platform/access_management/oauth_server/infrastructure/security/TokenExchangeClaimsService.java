package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.session.domain.constants.SessionStatus;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AM policy applied by the JWT customizer before Spring persists an exchange.
 * Spring remains responsible for protocol conversion, client authentication,
 * stored-token validation, may_act, token generation and response serialization.
 */
@Service
public class TokenExchangeClaimsService {
    private static final String ACCESS_TOKEN = "urn:ietf:params:oauth:token-type:access_token";
    private final TokenExchangeProperties properties;
    private final OAuthClientJpaRepository clients;
    private final ServiceAccountJpaRepository accounts;
    private final IGRPUserEntityRepository users;
    private final OAuth2AuthorizationService authorizations;
    private final SessionRepository sessions;

    public TokenExchangeClaimsService(TokenExchangeProperties properties, OAuthClientJpaRepository clients,
            ServiceAccountJpaRepository accounts, IGRPUserEntityRepository users,
            OAuth2AuthorizationService authorizations, SessionRepository sessions) {
        this.properties = properties;
        this.clients = clients;
        this.accounts = accounts;
        this.users = users;
        this.authorizations = authorizations;
        this.sessions = sessions;
    }

    @Transactional(readOnly = true)
    public void customize(JwtEncodingContext context) {
        OAuth2TokenExchangeAuthenticationToken request = context.getAuthorizationGrant();
        String caller = context.getRegisteredClient().getClientId();
        OAuthClientEntity sourceClient = activeClient(caller);
        require(sourceClient.getGrantTypes().contains(AuthorizationGrantType.TOKEN_EXCHANGE.getValue()),
                "unauthorized_client", "Client is not enabled for token exchange");
        require(request.getAudiences().size() == 1 && request.getResources().isEmpty(),
                "invalid_target", "Exactly one audience and no resource are supported");
        String audience = request.getAudiences().iterator().next();
        OAuthClientEntity target = activeClient(audience);
        ApplicationEntity application = target.getApplication();
        require(application != null && application.getId() != null && application.getStatus() == Status.ACTIVE,
                "invalid_target", "Audience must belong to an active application");
        require(ACCESS_TOKEN.equals(request.getSubjectTokenType())
                        && ACCESS_TOKEN.equals(request.getRequestedTokenType()),
                "invalid_request", "Only access tokens are supported");

        OAuth2Authorization subject = context.getAuthorization();
        require(subject != null && subject.getAccessToken() != null && subject.getAccessToken().isActive(),
                "invalid_grant", "Subject token is not active");
        Map<String, Object> original = subject.getAccessToken().getClaims();
        require(original != null && original.get("sub") instanceof String,
                "invalid_grant", "Subject token has no user identity");
        String userId = (String) original.get("sub");
        String issuer = context.getAuthorizationServerContext().getIssuer();
        require(issuer.equals(original.get("iss")), "invalid_grant", "Subject issuer is not accepted");
        var user = users.findById(userId)
                .orElseThrow(() -> error("invalid_grant", "Subject user is unavailable"));
        require(user.getStatus() == Status.ACTIVE || user.getStatus() == Status.TEMPORARY,
                "invalid_grant", "Subject user is unavailable");

        // Respect the selected role and its assignment; never union unrelated roles.
        RoleEntity role = user.getActiveRole();
        require(role != null && role.getId() != null && role.getStatus() == Status.ACTIVE
                        && role.getCode() != null && !role.getCode().isBlank()
                        && user.getRoles().stream().anyMatch(r -> role.getId().equals(r.getId()))
                        && role.getApplications().stream().anyMatch(a -> application.getId().equals(a.getId())),
                "access_denied", "Active role does not grant access to the target application");
        require(Objects.equals(original.get("selectedRole"), role.getCode()),
                "invalid_grant", "Selected role changed; obtain a new subject token");
        String org = role.getDepartment() == null ? "" : role.getDepartment().getCode();
        require(Objects.equals(original.get("org"), org),
                "invalid_grant", "Organization changed; obtain a new subject token");
        Set<String> scopes = context.getAuthorizedScopes();
        require(subject.getAuthorizedScopes().containsAll(scopes)
                        && sourceClient.getScopes().containsAll(scopes) && target.getScopes().containsAll(scopes),
                "invalid_scope", "Scopes must be permitted by the subject, caller and target");

        Instant expiry = context.getClaims().build().getExpiresAt();
        require(expiry != null && target.getAccessTokenTtl() > 0,
                "invalid_target", "Invalid target token lifetime");
        expiry = earlier(expiry, subject.getAccessToken().getToken().getExpiresAt());
        expiry = earlier(expiry, Instant.now().plusSeconds(target.getAccessTokenTtl()));
        String actorId = caller;
        if (request.getActorToken() != null) {
            require(ACCESS_TOKEN.equals(request.getActorTokenType()),
                    "invalid_request", "actor_token_type must be access_token");
            OAuth2Authorization actor = authorizations.findByToken(request.getActorToken(), OAuth2TokenType.ACCESS_TOKEN);
            require(actor != null && actor.getAccessToken() != null && actor.getAccessToken().isActive()
                            && actor.getRegisteredClientId().equals(sourceClient.getId().toString())
                            && AuthorizationGrantType.CLIENT_CREDENTIALS.equals(actor.getAuthorizationGrantType()),
                    "invalid_grant", "Actor must be an active client_credentials token of the caller");
            var account = accounts.findByOauthClient_ClientId(caller)
                    .filter(a -> a.isActive() && a.getOauthClient().isActive())
                    .orElseThrow(() -> error("invalid_grant", "Actor service account is unavailable"));
            var actorClaims = actor.getAccessToken().getClaims();
            actorId = account.getId().toString();
            require(actorClaims != null && actorId.equals(actorClaims.get("sub"))
                            && issuer.equals(actorClaims.get("iss")),
                    "invalid_grant", "Actor identity does not match the caller service account");
            expiry = earlier(expiry, actor.getAccessToken().getToken().getExpiresAt());
        } else {
            require(request.getActorTokenType() == null, "invalid_request", "Unexpected actor_token_type");
        }

        // Minimal compatibility with the existing session layer: do not mint a new login session.
        UUID sid;
        try {
            sid = UUID.fromString(String.valueOf(original.get("sid")));
        } catch (IllegalArgumentException ex) {
            throw error("invalid_grant", "Subject token has no valid session");
        }
        var session = sessions.findBySessionId(sid)
                .orElseThrow(() -> error("invalid_grant", "Subject session is unavailable"));
        require(userId.equals(session.getUserId()) && session.getStatus() == SessionStatus.ACTIVE
                        && session.getExpiresAt().isAfter(Instant.now())
                        && (session.getAbsoluteExpiresAt() == null || session.getAbsoluteExpiresAt().isAfter(Instant.now())),
                "invalid_grant", "Subject session is inactive");
        expiry = earlier(expiry, session.getExpiresAt());
        if (session.getAbsoluteExpiresAt() != null) expiry = earlier(expiry, session.getAbsoluteExpiresAt());
        require(expiry.isAfter(Instant.now()), "invalid_grant", "Exchange lifetime exhausted");

        Map<String, Object> actorChain = new LinkedHashMap<>();
        actorChain.put("sub", actorId);
        actorChain.put("iss", issuer);
        if (original.containsKey("act")) {
            actorChain.put("act", copyActorChain(original.get("act"), 1));
        }
        Set<String> permissions = role.getPermissions().stream()
                .filter(p -> p.getStatus() == Status.ACTIVE && belongsTo(p, application))
                .map(p -> p.getPermissionKey() != null && !p.getPermissionKey().isBlank()
                        ? p.getPermissionKey() : p.getName())
                .filter(name -> name != null && !name.isBlank())
                .collect(Collectors.toSet());
        context.getClaims().subject(userId).audience(List.of(audience)).expiresAt(expiry)
                .claim("selectedRole", role.getCode()).claim("org", org)
                .claim("is_super_admin", false)
                .claim("permissions", permissions)
                .claim("resource_access", Map.of(audience, Map.of("roles", List.of(role.getCode()))))
                .claim("act", actorChain).claim("sid", sid.toString());
        if (original.get("device_id") instanceof String deviceId) {
            context.getClaims().claim("device_id", deviceId);
        }
    }

    private OAuthClientEntity activeClient(String clientId) {
        return clients.findByClientId(clientId).filter(OAuthClientEntity::isActive)
                .orElseThrow(() -> error("invalid_target", "Client or audience is unavailable"));
    }

    private boolean belongsTo(PermissionEntity permission, ApplicationEntity application) {
        if (permission.getApplication() != null) {
            return application.getId().equals(permission.getApplication().getId());
        }
        // Legacy permissions are owned through resource/application associations.
        return permission.getResources().stream()
                .filter(r -> r.getStatus() == Status.ACTIVE)
                .anyMatch(r -> r.getApplications().stream().anyMatch(a -> application.getId().equals(a.getId())));
    }

    private Map<String, Object> copyActorChain(Object value, int depth) {
        require(depth < properties.getMaxActorDepth(), "invalid_request", "Actor chain is too deep");
        require(value instanceof Map<?, ?>, "invalid_grant", "Malformed actor chain");
        Map<?, ?> actor = (Map<?, ?>) value;
        require(actor.get("sub") instanceof String sub && !sub.isBlank(),
                "invalid_grant", "Malformed actor identity");
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put("sub", actor.get("sub"));
        if (actor.get("iss") instanceof String iss) copy.put("iss", iss);
        if (actor.containsKey("act")) copy.put("act", copyActorChain(actor.get("act"), depth + 1));
        return copy;
    }

    private static Instant earlier(Instant first, Instant second) {
        require(second != null, "invalid_grant", "Input token must expire");
        return first.isBefore(second) ? first : second;
    }

    private static void require(boolean condition, String code, String description) {
        if (!condition) throw error(code, description);
    }

    private static OAuth2AuthenticationException error(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null));
    }
}
