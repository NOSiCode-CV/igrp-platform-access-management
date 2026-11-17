package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.security_audit.application.service.AccessAuditLogger;
import cv.igrp.platform.access_management.shared.domain.events.UserAccessEndedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcLogoutAuthenticationToken;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RP-initiated logout (#33; spec "Logout, kill and refresh"): the user's access ends on the whole
 * platform (every authorization, then every key after commit), the logout is audited, and the
 * response either cascades to the upstream IdP or redirects back to the caller.
 */
@ExtendWith(MockitoExtension.class)
class SessionLogoutHandlerTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000099";
    private static final String POST_LOGOUT = "https://app.igrp/post-logout";

    @Mock
    private OAuth2AuthorizationService authorizationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AccessAuditLogger accessAuditLogger;
    @Mock
    private UserAuthorizations userAuthorizations;

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/connect/logout");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void endsTheUsersAccessAuditsAndRedirectsBack() throws Exception {
        OidcIdToken idToken = idToken();

        handler(null, true).onAuthenticationSuccess(request, response, logoutToken(idToken, POST_LOGOUT, "xyz"));

        verify(userAuthorizations).removeAll(USER_ID);
        verify(eventPublisher).publishEvent(new UserAccessEndedEvent(Set.of(USER_ID), "USER_LOGOUT"));
        verify(accessAuditLogger).recordLogout(USER_ID);
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).isEqualTo(POST_LOGOUT + "?state=xyz");
    }

    @Test
    void noPostLogoutRedirect_yields200() throws Exception {
        handler(null, true).onAuthenticationSuccess(request, response, logoutToken(idToken(), null, null));

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void cascadesToTheIdpWithTheSignInsUpstreamIdToken() throws Exception {
        OidcIdToken idToken = idToken();
        when(authorizationService.findByToken(idToken.getTokenValue(), null))
                .thenReturn(authorizationSignedInWith("upstream-id-token"));

        handler(idpRegistrations(), true).onAuthenticationSuccess(request, response,
                logoutToken(idToken, POST_LOGOUT, "xyz"));

        assertThat(response.getRedirectedUrl())
                .startsWith("https://idp.example/logout?client_id=am-at-idp")
                .contains("id_token_hint=upstream-id-token")
                .contains("post_logout_redirect_uri=" + POST_LOGOUT)
                .contains("state=xyz");
        verify(userAuthorizations).removeAll(USER_ID);
    }

    @Test
    void aFederatedSignInEndsTheInternalUsersAccess() throws Exception {
        // The ID token's subject is the authorization's principal: the upstream IdP's subject.
        OidcIdToken idToken = new OidcIdToken("tok-" + UUID.randomUUID(), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(30), Map.of("sub", "upstream-subject"));
        when(authorizationService.findByToken(idToken.getTokenValue(), null))
                .thenReturn(authorizationSignedInWith("upstream-id-token"));

        handler(null, true).onAuthenticationSuccess(request, response, logoutToken(idToken, POST_LOGOUT, null));

        verify(userAuthorizations).removeAll(USER_ID);
        verify(eventPublisher).publishEvent(new UserAccessEndedEvent(Set.of(USER_ID), "USER_LOGOUT"));
        verify(accessAuditLogger).recordLogout(USER_ID);
    }

    @Test
    void cascadesWithoutAHintWhenTheSignInIsUnknown() throws Exception {
        handler(idpRegistrations(), true).onAuthenticationSuccess(request, response,
                logoutToken(idToken(), POST_LOGOUT, null));

        assertThat(response.getRedirectedUrl())
                .startsWith("https://idp.example/logout?client_id=am-at-idp")
                .doesNotContain("id_token_hint");
    }

    @Test
    void noCascadeWhenTurnedOff() throws Exception {
        handler(idpRegistrations(), false).onAuthenticationSuccess(request, response,
                logoutToken(idToken(), POST_LOGOUT, null));

        assertThat(response.getRedirectedUrl()).isEqualTo(POST_LOGOUT);
    }

    @Test
    void clearsTheAuthServerCookies() throws Exception {
        request.setCookies(new jakarta.servlet.http.Cookie("JSESSIONID", "abc"),
                new jakarta.servlet.http.Cookie("other", "keep"));

        handler(null, true).onAuthenticationSuccess(request, response, logoutToken(idToken(), null, null));

        assertThat(response.getHeaders("Set-Cookie"))
                .singleElement().asString().startsWith("JSESSIONID=; Path=/; Max-Age=0");
    }

    // ---- helpers ----

    private SessionLogoutHandler handler(ClientRegistrationRepository registrations, boolean cascade) {
        return new SessionLogoutHandler(authorizationService, eventPublisher, accessAuditLogger, userAuthorizations,
                provider(registrations), "external-idp", cascade);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ClientRegistrationRepository> provider(ClientRegistrationRepository value) {
        ObjectProvider<ClientRegistrationRepository> provider = mock(ObjectProvider.class);
        org.mockito.Mockito.lenient().when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static ClientRegistrationRepository idpRegistrations() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("external-idp")
                .clientId("am-at-idp")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://am/login/oauth2/code/external-idp")
                .authorizationUri("https://idp.example/auth")
                .tokenUri("https://idp.example/token")
                .providerConfigurationMetadata(Map.of("end_session_endpoint", "https://idp.example/logout"))
                .build();
        return id -> "external-idp".equals(id) ? registration : null;
    }

    private static OidcIdToken idToken() {
        return new OidcIdToken("tok-" + UUID.randomUUID(), Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(30), Map.of("sub", USER_ID));
    }

    /** An authorization whose sign-in, stored by Spring AS, carries the upstream IdP's ID token. */
    private static OAuth2Authorization authorizationSignedInWith(String upstreamIdToken) {
        OidcIdToken upstream = new OidcIdToken(upstreamIdToken, Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60), Map.of("sub", "upstream-subject"));
        OAuth2AuthenticationToken signIn = new OAuth2AuthenticationToken(
                new DefaultOidcUser(List.of(), upstream), List.of(), "external-idp");
        RegisteredClient client = RegisteredClient.withId("test-client")
                .clientId("test-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app/cb")
                .build();
        OAuth2AccessToken access = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(240));
        return OAuth2Authorization.withRegisteredClient(client)
                .principalName("upstream-subject")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(Principal.class.getName(), signIn)
                .token(access, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", USER_ID)))
                .build();
    }

    private static OidcLogoutAuthenticationToken logoutToken(OidcIdToken idToken, String postLogoutRedirectUri,
                                                             String state) {
        return new OidcLogoutAuthenticationToken(idToken, new UsernamePasswordAuthenticationToken(USER_ID, "n/a"),
                "JSESSIONID-test", "test-client", postLogoutRedirectUri, state);
    }
}
