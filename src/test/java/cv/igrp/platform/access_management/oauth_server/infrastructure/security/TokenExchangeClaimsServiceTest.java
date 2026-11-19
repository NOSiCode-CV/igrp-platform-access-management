package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenExchangeAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** v3-20 (#28): AM's token-exchange policy; the exchanged token is thin (spec "Tokens"). */
class TokenExchangeClaimsServiceTest {
    private final OAuthClientJpaRepository clients = mock(OAuthClientJpaRepository.class);
    private final IGRPUserEntityRepository users = mock(IGRPUserEntityRepository.class);
    private final OAuth2AuthorizationService authorizations = mock(OAuth2AuthorizationService.class);
    private final ServiceAccountJpaRepository accounts = mock(ServiceAccountJpaRepository.class);
    private final TokenExchangeProperties properties = new TokenExchangeProperties();
    private final String userId = UUID.randomUUID().toString();
    private final Instant now = Instant.now();
    private TokenExchangeClaimsService service;
    private JwtEncodingContext context;
    private JwtClaimsSet.Builder claims;
    private OAuthClientEntity target;
    private IGRPUserEntity user;

    @BeforeEach
    void setup() {
        service = new TokenExchangeClaimsService(properties, clients, accounts,
                users, authorizations);
        var app = new ApplicationEntity();
        app.setId(2);
        app.setStatus(Status.ACTIVE);
        var source = client("a");
        target = client("b");
        target.setApplication(app);
        when(clients.findByClientId("a")).thenReturn(Optional.of(source));
        when(clients.findByClientId("b")).thenReturn(Optional.of(target));
        user = new IGRPUserEntity();
        user.setId(userId);
        user.setStatus(Status.ACTIVE);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        RegisteredClient registered = RegisteredClient.withId(source.getId().toString()).clientId("a")
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).scope("read").build();
        var subject = OAuth2Authorization.withRegisteredClient(registered).principalName(userId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).authorizedScopes(Set.of("read"))
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "subject", now, now.plusSeconds(300)),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                                Map.of("sub", userId, "iss", "https://am", "device_id", "device-1")))
                .build();
        var principal = UsernamePasswordAuthenticationToken.authenticated("a", "", List.of());
        var request = new OAuth2TokenExchangeAuthenticationToken(
                "urn:ietf:params:oauth:token-type:access_token", "subject",
                "urn:ietf:params:oauth:token-type:access_token", principal,
                null, null, Set.of(), Set.of("b"), Set.of("read"), Map.of());
        var server = mock(AuthorizationServerContext.class);
        when(server.getIssuer()).thenReturn("https://am");
        claims = JwtClaimsSet.builder().issuer("https://am").expiresAt(now.plusSeconds(900));
        context = mock(JwtEncodingContext.class);
        when(context.getAuthorizationGrant()).thenReturn(request);
        when(context.getRegisteredClient()).thenReturn(registered);
        when(context.getAuthorization()).thenReturn(subject);
        when(context.getAuthorizationServerContext()).thenReturn(server);
        when(context.getAuthorizedScopes()).thenReturn(Set.of("read"));
        when(context.getClaims()).thenReturn(claims);
    }

    @Test
    void thinTokenForTheTarget() {
        assertEquals(userId, service.customize(context));
        var result = claims.build();
        assertEquals(userId, result.getSubject());
        assertEquals(List.of("b"), result.getAudience());
        assertEquals(Map.of("sub", "a", "iss", "https://am"), result.getClaim("act"));
        assertEquals("device-1", result.getClaim("device_id"));
        // No longer than the subject token (300 s), the target's lifetime (600 s) or the default (900 s).
        assertEquals(now.plusSeconds(300), result.getExpiresAt());
        // #33: no permission, role, organisation or session in a token.
        for (String fat : List.of("permissions", "resource_access", "selectedRole", "org", "sid")) {
            assertFalse(result.getClaims().containsKey(fat), fat);
        }
        verifyNoInteractions(authorizations);
    }

    @Test void targetLifetimeCapsTheToken() {
        target.setAccessTokenTtl(60);
        service.customize(context);
        Instant exp = claims.build().getExpiresAt();
        assertTrue(!exp.isAfter(Instant.now().plusSeconds(60)) && exp.isAfter(now), exp.toString());
    }
    @Test void deniesUnknownDestination() {
        when(clients.findByClientId("b")).thenReturn(Optional.empty());
        assertError("invalid_target");
    }
    @Test void deniesUnknownCaller() {
        when(clients.findByClientId("a")).thenReturn(Optional.empty());
        assertError("invalid_target");
    }
    @Test void deniesCallerWithoutTheGrant() {
        clients.findByClientId("a").orElseThrow().setGrantTypes(Set.of("client_credentials"));
        assertError("unauthorized_client");
    }
    @Test void deniesTargetWithoutApplication() {
        target.setApplication(null);
        assertError("invalid_target");
    }
    @Test void deniesInactiveTargetApplication() {
        target.getApplication().setStatus(Status.INACTIVE);
        assertError("invalid_target");
    }
    @Test void deniesSeveralAudiences() {
        exchange(new LinkedHashSet<>(List.of("b", "c")), "urn:ietf:params:oauth:token-type:access_token");
        assertError("invalid_target");
    }
    @Test void deniesOtherTokenTypes() {
        exchange(Set.of("b"), "urn:ietf:params:oauth:token-type:id_token");
        assertError("invalid_request");
    }
    @Test void deniesForeignIssuer() {
        subjectClaims(Map.of("sub", userId, "iss", "https://elsewhere"));
        assertError("invalid_grant");
    }
    @Test void acceptsIssuerReadBackAsUrl() throws Exception {
        subjectClaims(Map.of("sub", userId, "iss", java.net.URI.create("https://am").toURL()));
        assertEquals(userId, service.customize(context));
    }
    @Test void deniesSubjectWithoutIssuer() {
        subjectClaims(Map.of("sub", userId));
        assertError("invalid_grant");
    }
    @Test void deniesSubjectWithoutUser() {
        subjectClaims(Map.of("iss", "https://am"));
        assertError("invalid_grant");
    }
    @Test void deniesUnknownUser() {
        when(users.findById(userId)).thenReturn(Optional.empty());
        assertError("invalid_grant");
    }
    @Test void acceptsTemporaryUser() {
        user.setStatus(Status.TEMPORARY);
        assertEquals(userId, service.customize(context));
    }
    @Test void deniesScopeExpansionAtTarget() {
        target.setScopes(Set.of());
        assertError("invalid_scope");
    }
    @Test void deniesTargetWithoutLifetime() {
        target.setAccessTokenTtl(0);
        assertError("invalid_target");
    }
    @Test void deniesDisabledUser() {
        user.setStatus(Status.INACTIVE);
        assertError("invalid_grant");
    }
    @Test void acceptsActorOwnedByCaller() {
        actorRequest(false);
        service.customize(context);
        Map<String, Object> act = claims.build().getClaim("act");
        assertEquals("11111111-1111-1111-1111-111111111111", act.get("sub"));
    }
    @Test void deniesActorOwnedByAnotherClient() {
        actorRequest(true);
        assertError("invalid_grant");
    }
    @Test void preservesPreviousActorAndEnforcesDepth() {
        var subject = context.getAuthorization();
        Map<String, Object> original = new HashMap<>(subject.getAccessToken().getClaims());
        original.put("act", Map.of("sub", "previous", "iss", "https://am"));
        var chained = OAuth2Authorization.from(subject)
                .token(subject.getAccessToken().getToken(), metadata ->
                        metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, original)).build();
        when(context.getAuthorization()).thenReturn(chained);
        service.customize(context);
        Map<String, Object> act = claims.build().getClaim("act");
        assertEquals(Map.of("sub", "previous", "iss", "https://am"), act.get("act"));
        properties.setMaxActorDepth(1);
        assertError("invalid_request");
    }
    private void actorRequest(boolean differentClient) {
        var source = clients.findByClientId("a").orElseThrow();
        var account = new cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity();
        account.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        account.setOauthClient(source);
        when(accounts.findByOauthClient_ClientId("a")).thenReturn(Optional.of(account));
        var registered = RegisteredClient.withId(differentClient ? "other-id" : source.getId().toString())
                .clientId(differentClient ? "other" : "a")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        var actor = OAuth2Authorization.withRegisteredClient(registered).principalName("a")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "actor", now, now.plusSeconds(200)),
                        metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                                Map.of("sub", account.getId().toString(), "iss", "https://am"))).build();
        when(authorizations.findByToken("actor", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(actor);
        var request = new OAuth2TokenExchangeAuthenticationToken(
                "urn:ietf:params:oauth:token-type:access_token", "subject",
                "urn:ietf:params:oauth:token-type:access_token",
                UsernamePasswordAuthenticationToken.authenticated("a", "", List.of()),
                "actor", "urn:ietf:params:oauth:token-type:access_token", Set.of(), Set.of("b"), Set.of("read"), Map.of());
        when(context.getAuthorizationGrant()).thenReturn(request);
    }
    @Test void deniesActorOfAnotherType() {
        actorRequest(false);
        actorExchange("actor", "urn:ietf:params:oauth:token-type:id_token");
        assertError("invalid_request");
    }
    @Test void deniesActorTypeWithoutActor() {
        actorExchange(null, "urn:ietf:params:oauth:token-type:access_token");
        assertError("invalid_request");
    }
    @Test void deniesActorWithoutServiceAccount() {
        actorRequest(false);
        when(accounts.findByOauthClient_ClientId("a")).thenReturn(Optional.empty());
        assertError("invalid_grant");
    }
    @Test void deniesMalformedActorChain() {
        subjectClaims(Map.of("sub", userId, "iss", "https://am", "act", Map.of("iss", "https://am")));
        assertError("invalid_grant");
    }
    private void subjectClaims(Map<String, Object> values) {
        var subject = context.getAuthorization();
        var replaced = OAuth2Authorization.from(subject)
                .token(subject.getAccessToken().getToken(), metadata ->
                        metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, values)).build();
        when(context.getAuthorization()).thenReturn(replaced);
    }
    private void exchange(Set<String> audiences, String subjectType) {
        var request = new OAuth2TokenExchangeAuthenticationToken(
                "urn:ietf:params:oauth:token-type:access_token", "subject", subjectType,
                UsernamePasswordAuthenticationToken.authenticated("a", "", List.of()),
                null, null, Set.of(), audiences, Set.of("read"), Map.of());
        when(context.getAuthorizationGrant()).thenReturn(request);
    }
    private void actorExchange(String actorToken, String actorType) {
        var request = new OAuth2TokenExchangeAuthenticationToken(
                "urn:ietf:params:oauth:token-type:access_token", "subject",
                "urn:ietf:params:oauth:token-type:access_token",
                UsernamePasswordAuthenticationToken.authenticated("a", "", List.of()),
                actorToken, actorType, Set.of(), Set.of("b"), Set.of("read"), Map.of());
        when(context.getAuthorizationGrant()).thenReturn(request);
    }
    private void assertError(String code) {
        assertEquals(code, assertThrows(OAuth2AuthenticationException.class,
                () -> service.customize(context)).getError().getErrorCode());
    }
    private OAuthClientEntity client(String id) {
        var client = new OAuthClientEntity();
        client.setId(UUID.randomUUID());
        client.setClientId(id);
        client.setGrantTypes(Set.of(AuthorizationGrantType.TOKEN_EXCHANGE.getValue()));
        client.setScopes(Set.of("read"));
        client.setAccessTokenTtl(600);
        return client;
    }
}
