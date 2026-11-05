package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Boot 4 migration gate — the {@code oauth2_authorization} store with
 * the payload that matters most: a browser login. The authorization carries
 * the iGRP OIDC principal ({@link IgrpOidcUser} with its {@link UserProfile},
 * built with {@code List.of()} like the real converters do), the saved
 * authorization request with its PKCE data, and every token type with claims
 * metadata.
 *
 * <p>Written and read through the production {@link OAuth2AuthorizationService}
 * bean against Postgres, so it exercises the real row and parameter mappers and
 * the real serializer. On the Jackson 3 store this is the proof that the new
 * allowlist covers every type a login stores.
 */
class AuthorizationStoreRoundTripIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;
    @Autowired
    OAuth2AuthorizationService authorizationService;
    @Autowired
    RegisteredClientRepository registeredClientRepository;

    @Test
    @DisplayName("An authorization_code login with the iGRP OIDC principal survives Postgres intact")
    void browserLoginAuthorizationRoundTrips() {
        CompatSupport.M2MClient client = new CompatSupport(context).registerM2MClient("compat-store-roundtrip");
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(client.clientId());
        assertNotNull(registeredClient);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        String userId = UUID.randomUUID().toString();

        UserProfile profile = new UserProfile(userId, "https://idp.example", "Fulano de Tal", "fulano@example.cv",
                "+2389000000", "123456789", "pwd", List.of("pwd"));
        OidcIdToken upstreamIdToken = new OidcIdToken("upstream-id-token", now, now.plusSeconds(300),
                Map.of("sub", userId, "iss", "https://idp.example", "sid", "upstream-sid"));
        IgrpOidcUser principal = new IgrpOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), upstreamIdToken, profile);
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                principal, List.of(new SimpleGrantedAuthority("ROLE_USER")), "external-idp");

        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(CompatEnvironment.ISSUER + "/oauth2/authorize")
                .clientId(client.clientId())
                .redirectUri("https://app.example.cv/callback")
                .scopes(new LinkedHashSet<>(List.of("openid", "profile")))
                .state("state-123")
                .additionalParameters(Map.of(
                        PkceParameterNames.CODE_CHALLENGE, "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                        PkceParameterNames.CODE_CHALLENGE_METHOD, "S256"))
                .build();

        OAuth2AuthorizationCode code = new OAuth2AuthorizationCode("code-" + UUID.randomUUID(), now, now.plusSeconds(300));
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-" + UUID.randomUUID(), now, now.plusSeconds(900), Set.of("openid", "profile"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-" + UUID.randomUUID(), now, now.plusSeconds(86400));
        OidcIdToken idToken = new OidcIdToken("id-" + UUID.randomUUID(), now, now.plusSeconds(900),
                Map.of("sub", userId, "iss", CompatEnvironment.ISSUER, "sid", UUID.randomUUID().toString()));

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(UUID.randomUUID().toString())
                .principalName(userId)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid", "profile"))
                .attribute(Principal.class.getName(), authentication)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .token(code)
                .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
                        Map.of("sub", userId, "sid", "sid-1", "scope", List.of("openid", "profile"))))
                .refreshToken(refreshToken)
                .token(idToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, idToken.getClaims()))
                .build();

        authorizationService.save(authorization);

        OAuth2Authorization back = authorizationService.findById(authorization.getId());
        assertNotNull(back, "the row must read back");

        OAuth2AuthenticationToken backAuthentication = back.getAttribute(Principal.class.getName());
        assertNotNull(backAuthentication);
        IgrpOidcUser backUser = assertInstanceOf(IgrpOidcUser.class, backAuthentication.getPrincipal());
        assertEquals(profile.email(), backUser.getUserProfile().email());
        assertEquals(List.of("pwd"), backUser.getUserProfile().amr());
        assertEquals("upstream-sid", backUser.getIdToken().getClaimAsString("sid"));

        OAuth2AuthorizationRequest backRequest = back.getAttribute(OAuth2AuthorizationRequest.class.getName());
        assertNotNull(backRequest);
        assertEquals("state-123", backRequest.getState());
        assertEquals("S256", backRequest.getAdditionalParameters().get(PkceParameterNames.CODE_CHALLENGE_METHOD));

        assertEquals(code.getTokenValue(), back.getToken(OAuth2AuthorizationCode.class).getToken().getTokenValue());
        assertEquals(accessToken.getTokenValue(), back.getAccessToken().getToken().getTokenValue());
        assertEquals(accessToken.getScopes(), back.getAccessToken().getToken().getScopes());
        assertEquals("sid-1", back.getAccessToken().getClaims().get("sid"));
        assertEquals(refreshToken.getTokenValue(), back.getRefreshToken().getToken().getTokenValue());
        assertEquals(idToken.getTokenValue(), back.getToken(OidcIdToken.class).getToken().getTokenValue());

        // Lookup by token value, the path the token, introspection and
        // revocation endpoints take.
        assertNotNull(authorizationService.findByToken(accessToken.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN));
        assertNotNull(authorizationService.findByToken(refreshToken.getTokenValue(), OAuth2TokenType.REFRESH_TOKEN));
    }
}
