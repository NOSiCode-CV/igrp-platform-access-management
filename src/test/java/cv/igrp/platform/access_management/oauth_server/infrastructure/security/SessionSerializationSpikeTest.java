package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import org.junit.jupiter.api.DisplayName;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spike for {@code plans/shared-servlet-session/scope.md} §6.
 *
 * <p>R3.2 needs the servlet session in Redis so a login survives landing on a
 * different pod. The open question was whether an {@link ObjectMapper} built
 * like {@code AuthorizationServerConfig.authorizationStoreObjectMapper()} can
 * round-trip what the session actually holds — not just what the authorization
 * store already exercises.
 *
 * <p>Three payloads, in rising order of doubt:
 * <ol>
 *   <li>the {@code SecurityContext} with an {@link IgrpOidcUser} principal —
 *       already proven by the authorization store, included as a control;</li>
 *   <li>the saved {@link OAuth2AuthorizationRequest} with its PKCE verifier —
 *       <b>the object whose loss produces {@code authorization_request_not_found}</b>;</li>
 *   <li>{@link DefaultSavedRequest} from {@code HttpSessionRequestCache}.</li>
 * </ol>
 *
 * <p>A failure here is not a bug in main code. It is the spike reporting that
 * the mapper needs more than the three existing mixins, which is exactly what
 * the estimate depends on.
 */
class SessionSerializationSpikeTest {

    /** Mirrors the private AllowlistedClassMixin in AuthorizationServerConfig. */

    /** The mapper both stores use. */
    private JsonMapper sessionMapper() {
        return cv.igrp.platform.access_management.shared.security.SecurityStoreObjectMapperFactory
                .create(getClass().getClassLoader());
    }

    private IgrpOidcUser principal() {
        UserProfile profile = new UserProfile(
                "00000000-0000-0000-0000-000000000042", "https://idp.example",
                "Fulano de Tal", "fulano@example.cv", "+2389000000", "123456789",
                "pwd", List.of("pwd"));
        OidcIdToken idToken = new OidcIdToken(
                "token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "00000000-0000-0000-0000-000000000042", "iss", "https://idp.example"));
        return new IgrpOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, profile);
    }

    // ─── 1. control: the principal the authorization store already handles ───

    @Test
    @DisplayName("SecurityContext with an IgrpOidcUser round-trips, UserProfile intact")
    void securityContextRoundTrips() throws Exception {
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal(), List.of(new SimpleGrantedAuthority("ROLE_USER")), "external-idp");
        SecurityContextImpl context = new SecurityContextImpl(authentication);
        JsonMapper mapper = sessionMapper();

        String json = mapper.writeValueAsString(context);
        SecurityContextImpl back = mapper.readValue(json, SecurityContextImpl.class);

        assertInstanceOf(IgrpOidcUser.class, back.getAuthentication().getPrincipal());
        IgrpOidcUser user = (IgrpOidcUser) back.getAuthentication().getPrincipal();
        assertNotNull(user.getUserProfile(), "UserProfile must survive — IgrpOidcUser holds it");
        assertEquals("fulano@example.cv", user.getUserProfile().email());
        assertEquals(List.of("pwd"), user.getUserProfile().amr());
    }

    // ─── 2. the one that matters: the saved authorization request ────────────

    @Test
    @DisplayName("OAuth2AuthorizationRequest round-trips with its PKCE verifier")
    void authorizationRequestRoundTrips() throws Exception {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://idp.example/oauth2/authorize")
                .clientId("igrp-access-management")
                .redirectUri("https://app.example/login/oauth2/code/external-idp")
                .scopes(new java.util.LinkedHashSet<>(List.of("openid", "profile", "email")))
                .state("pwcc-UU7WQnnMgMkk39yb5nXT8fWWXPm7c8lO4N-gSo")
                .additionalParameters(Map.of("nonce", "n-0S6_WzA2Mj"))
                .attributes(Map.of(
                        PkceParameterNames.CODE_VERIFIER, "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        "registration_id", "external-idp"))
                .build();
        JsonMapper mapper = sessionMapper();

        String json = mapper.writeValueAsString(request);
        OAuth2AuthorizationRequest back = mapper.readValue(json, OAuth2AuthorizationRequest.class);

        assertEquals(request.getState(), back.getState(), "state is what the callback matches on");
        assertEquals(request.getRedirectUri(), back.getRedirectUri());
        assertEquals(AuthorizationGrantType.AUTHORIZATION_CODE, back.getGrantType());
        assertEquals(request.getScopes(), back.getScopes());
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                back.getAttribute(PkceParameterNames.CODE_VERIFIER),
                "without the PKCE verifier the token exchange fails after a pod change");
        assertEquals("n-0S6_WzA2Mj", back.getAdditionalParameters().get("nonce"));
    }

    // ─── 3. the saved request HttpSessionRequestCache stores ─────────────────

    @Test
    @DisplayName("DefaultSavedRequest round-trips")
    void savedRequestRoundTrips() throws Exception {
        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/oauth2/authorize");
        http.setServerName("am.example.cv");
        http.setServerPort(443);
        http.setScheme("https");
        http.setQueryString("response_type=code&client_id=igrp-access-management");
        DefaultSavedRequest saved = new DefaultSavedRequest(http);
        JsonMapper mapper = sessionMapper();

        String json = mapper.writeValueAsString(saved);
        DefaultSavedRequest back = mapper.readValue(json, DefaultSavedRequest.class);

        assertEquals(saved.getRedirectUrl(), back.getRedirectUrl());
        assertEquals(saved.getMethod(), back.getMethod());
    }
}
