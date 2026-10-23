package cv.igrp.platform.access_management.shared.config;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.web.PortResolverImpl;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3.2 — guards the {@code springSessionConversionService} that stores servlet
 * session attributes as JSON in Postgres.
 *
 * <p>This is the production path, not a mirror of it: it exercises the bean from
 * {@link SessionStoreConfig} rather than rebuilding a mapper, so a change to
 * either the factory or the converters is caught here.
 *
 * <p>The round trip matters more than it looks. The converter deserializes into
 * {@link Object}, with no target type, so it depends entirely on the class-name
 * default typing that {@code SecurityJackson2Modules} enables — and that typing
 * is allowlist-restricted. Any type reachable from a session attribute and
 * missing from the allowlist fails at runtime, during a login, as a 500 the user
 * reads as "Login could not be completed".
 */
class SessionStoreConversionTest {

    private final ConversionService conversionService =
            new SessionStoreConfig().springSessionConversionService();

    private Object roundTrip(Object attribute) {
        byte[] bytes = conversionService.convert(attribute, byte[].class);
        assertNotNull(bytes, "serialization produced no bytes");
        return conversionService.convert(bytes, Object.class);
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

    @Test
    @DisplayName("SPRING_SECURITY_CONTEXT survives the round trip with its UserProfile")
    void securityContextRoundTrips() {
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal(), List.of(new SimpleGrantedAuthority("ROLE_USER")), "external-idp");

        Object back = roundTrip(new SecurityContextImpl(authentication));

        SecurityContextImpl context = assertInstanceOf(SecurityContextImpl.class, back);
        IgrpOidcUser user = assertInstanceOf(IgrpOidcUser.class, context.getAuthentication().getPrincipal());
        assertNotNull(user.getUserProfile(), "UserProfile must survive — IgrpOidcUser holds it");
        assertEquals("fulano@example.cv", user.getUserProfile().email());
        // amr is built with List.of() on one of the two principal-construction
        // paths, which is the allowlist gap the R3.2 spike found.
        assertEquals(List.of("pwd"), user.getUserProfile().amr());
    }

    @Test
    @DisplayName("The saved authorization request survives with its PKCE verifier")
    void authorizationRequestRoundTrips() {
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://idp.example/oauth2/authorize")
                .clientId("igrp-access-management")
                .redirectUri("https://app.example/login/oauth2/code/external-idp")
                .scopes(new LinkedHashSet<>(List.of("openid", "profile", "email")))
                .state("pwcc-UU7WQnnMgMkk39yb5nXT8fWWXPm7c8lO4N-gSo")
                .additionalParameters(Map.of("nonce", "n-0S6_WzA2Mj"))
                .attributes(Map.of(
                        PkceParameterNames.CODE_VERIFIER, "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                        "registration_id", "external-idp"))
                .build();

        Object back = roundTrip(request);

        OAuth2AuthorizationRequest restored = assertInstanceOf(OAuth2AuthorizationRequest.class, back);
        assertEquals(request.getState(), restored.getState(),
                "state is what the IdP callback matches on — losing it IS authorization_request_not_found");
        assertEquals(request.getRedirectUri(), restored.getRedirectUri());
        assertEquals(request.getScopes(), restored.getScopes());
        assertEquals("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
                restored.getAttribute(PkceParameterNames.CODE_VERIFIER),
                "without the PKCE verifier the token exchange fails after a pod change");
        assertEquals("n-0S6_WzA2Mj", restored.getAdditionalParameters().get("nonce"));
    }

    @Test
    @DisplayName("The saved request from HttpSessionRequestCache survives")
    void savedRequestRoundTrips() {
        MockHttpServletRequest http = new MockHttpServletRequest("GET", "/oauth2/authorize");
        http.setServerName("am.example.cv");
        http.setServerPort(443);
        http.setScheme("https");
        http.setQueryString("response_type=code&client_id=igrp-access-management");
        DefaultSavedRequest saved = new DefaultSavedRequest(http, new PortResolverImpl());

        Object back = roundTrip(saved);

        DefaultSavedRequest restored = assertInstanceOf(DefaultSavedRequest.class, back);
        assertEquals(saved.getRedirectUrl(), restored.getRedirectUrl());
        assertEquals(saved.getMethod(), restored.getMethod());
    }

    @Test
    @DisplayName("A type outside the allowlist fails loudly rather than storing a broken session")
    void unknownTypeFailsLoudly() {
        // Not a smoke test for Jackson: it pins the failure mode. A silent
        // failure here would store an unreadable attribute and sign the user out
        // on the next request, with nothing naming the attribute at fault.
        record NotAllowlisted(String value) {
        }

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> roundTrip(new NotAllowlisted("x")));

        // The write succeeds and the READ fails: an unknown type serializes to
        // an object with no type information, and only the round trip back finds
        // it unreadable. That is precisely why the message has to be actionable
        // — the damage is already in the table by the time anyone sees it.
        // ConversionService wraps the converter's exception, so walk the chain.
        assertTrue(describeCauses(failure).contains("SecurityStoreObjectMapperFactory"),
                "the error must name where to add the missing allowlist entry, got: "
                        + describeCauses(failure));
    }

    private static String describeCauses(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            chain.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(" | ");
            if (t.getCause() == t) {
                break;
            }
        }
        return chain.toString();
    }
}
