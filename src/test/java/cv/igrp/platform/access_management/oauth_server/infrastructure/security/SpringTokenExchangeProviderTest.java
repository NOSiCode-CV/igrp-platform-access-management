package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Characterizes the real Spring provider, without HTTP or a database. Not a full endpoint IT. */
class SpringTokenExchangeProviderTest {
    @AfterEach void clearContext() { AuthorizationServerContextHolder.resetContext(); }

    @Test
    void providerAcceptsOptionalActorAndPersistsExchangeWithoutRefreshToken() {
        var store = mock(OAuth2AuthorizationService.class);
        @SuppressWarnings("unchecked")
        OAuth2TokenGenerator<Jwt> generator = mock(OAuth2TokenGenerator.class);
        var registered = RegisteredClient.withId("a-id").clientId("a")
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE).scope("read").build();
        var user = UsernamePasswordAuthenticationToken.authenticated("user", "", List.of());
        Instant now = Instant.now();
        var subject = OAuth2Authorization.withRegisteredClient(registered).principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("read"))
                .attribute(Principal.class.getName(), user)
                .accessToken(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "subject", now, now.plusSeconds(300)))
                .build();
        when(store.findByToken("subject", OAuth2TokenType.ACCESS_TOKEN)).thenReturn(subject);
        var server = mock(AuthorizationServerContext.class);
        when(server.getIssuer()).thenReturn("https://am");
        AuthorizationServerContextHolder.setContext(server);
        when(generator.generate(any())).thenAnswer(invocation -> {
            org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext context = invocation.getArgument(0);
            assertSame(subject, context.getAuthorization());
            assertEquals("a", context.getRegisteredClient().getClientId());
            assertSame(user, context.getPrincipal());
            assertEquals(AuthorizationGrantType.TOKEN_EXCHANGE, context.getAuthorizationGrantType());
            return Jwt.withTokenValue("exchanged").header("alg", "RS256").subject("user")
                    .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        });
        var client = new OAuth2ClientAuthenticationToken(registered, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret");
        var request = new OAuth2TokenExchangeAuthenticationToken(
                "urn:ietf:params:oauth:token-type:access_token", "subject",
                "urn:ietf:params:oauth:token-type:access_token", client,
                null, null, Set.of(), Set.of("b"), Set.of("read"), Map.of());
        var provider = new OAuth2TokenExchangeAuthenticationProvider(store, generator);
        var result = (OAuth2AccessTokenAuthenticationToken) provider.authenticate(request);
        assertNotNull(result);
        assertNull(result.getRefreshToken());
        assertEquals("exchanged", result.getAccessToken().getTokenValue());
        verify(store).save(argThat(saved -> saved.getAuthorizationGrantType().equals(AuthorizationGrantType.TOKEN_EXCHANGE)
                && saved.getRegisteredClientId().equals("a-id")));
    }
}
