package cv.igrp.platform.access_management.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1.6 — the SSO login id has to survive serialization, or the cascade is
 * silently partial.
 *
 * <p>The id is minted during the browser login and read again at
 * {@code /oauth2/token} — a server-to-server call with no cookie of its own.
 * The only thing connecting the two is the principal, which Spring
 * Authorization Server stores as a JSON attribute of the
 * {@code OAuth2Authorization}. If the field does not round-trip, every session
 * gets a null key, no cascade ever happens, and nothing fails loudly: logout
 * just keeps signing the user out of one application, exactly as before.
 *
 * <p>Uses the real {@code SecurityStoreObjectMapperFactory}, so this covers both
 * stores that mapper now feeds.
 */
class IgrpOidcUserSerializationTest {

    private final ObjectMapper mapper =
            SecurityStoreObjectMapperFactory.create(getClass().getClassLoader());

    private UserProfile profile() {
        return new UserProfile(
                "00000000-0000-0000-0000-000000000042", "https://idp.example",
                "Fulano de Tal", "fulano@example.cv", "+2389000000", "123456789",
                "pwd", List.of("pwd"));
    }

    private OidcIdToken idToken() {
        return new OidcIdToken(
                "token-value", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", "00000000-0000-0000-0000-000000000042", "iss", "https://idp.example"));
    }

    @Test
    @DisplayName("The login id survives a round trip on the principal")
    void loginIdRoundTrips() throws Exception {
        String loginId = "Zm9vYmFyYmF6cXV1eHg";
        IgrpOidcUser principal = new IgrpOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken(), profile(), loginId);

        String json = mapper.writeValueAsString(principal);
        IgrpOidcUser back = mapper.readValue(json, IgrpOidcUser.class);

        assertEquals(loginId, back.getSsoLoginId(),
                "without this the session key is always null and no logout ever cascades");
        assertNotNull(back.getUserProfile());
    }

    @Test
    @DisplayName("The login id survives inside a full SecurityContext")
    void loginIdRoundTripsInsideSecurityContext() throws Exception {
        String loginId = "Zm9vYmFyYmF6cXV1eHg";
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                new IgrpOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                        idToken(), profile(), loginId),
                List.of(new SimpleGrantedAuthority("ROLE_USER")), "external-idp");

        String json = mapper.writeValueAsString(new SecurityContextImpl(authentication));
        SecurityContextImpl back = mapper.readValue(json, SecurityContextImpl.class);

        IgrpOidcUser principal = assertInstanceOf(IgrpOidcUser.class,
                back.getAuthentication().getPrincipal());
        assertEquals(loginId, principal.getSsoLoginId());
    }

    @Test
    @DisplayName("A principal stored before the field existed deserializes with a null id")
    void absentFieldDeserializesToNull() throws Exception {
        // N2 / backwards compatibility. Authorizations and servlet sessions
        // persisted before R1.6 have no ssoLoginId property. They must load with
        // null — meaning "no key", single-row logout — rather than failing and
        // breaking a live user's session at deploy time.
        IgrpOidcUser legacy = new IgrpOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken(), profile());
        String json = mapper.writeValueAsString(legacy);
        assertFalse(json.contains("ssoLoginId") && !json.contains("\"ssoLoginId\":null"),
                "a null id should not be written as a value other than null");

        IgrpOidcUser back = mapper.readValue(json, IgrpOidcUser.class);

        assertNull(back.getSsoLoginId());
        assertNotNull(back.getUserProfile(), "the rest of the principal must still load");
    }
}
