package cv.igrp.platform.access_management.users.application.listener;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.ServiceAccountTokenClaims;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserIdentityResolutionListenerTest {

    @Mock
    private UserIdentityResolutionService identityResolutionService;

    @InjectMocks
    private UserIdentityResolutionListener listener;

    private Jwt userJwt;
    private Jwt serviceAccountJwt;
    private Jwt anonymousM2MJwt;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        Map<String, Object> userClaims = new LinkedHashMap<>();
        userClaims.put("sub", "00000000-0000-0000-0000-000000000123");
        userClaims.put("sid", "11111111-1111-1111-1111-111111111111");
        userJwt = new Jwt("user-token", now, now.plusSeconds(60),
                Map.of("alg", "RS256"), userClaims);

        Map<String, Object> saClaims = new LinkedHashMap<>();
        saClaims.put("sub", "00000000-0000-0000-0000-000000000099");
        saClaims.put(ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE,
                ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT);
        saClaims.put(ServiceAccountTokenClaims.CLAIM_CLIENT_ID, "client-x");
        serviceAccountJwt = new Jwt("sa-token", now, now.plusSeconds(60),
                Map.of("alg", "RS256"), saClaims);

        // Pre-V9 / fallback M2M shape: no principalType marker, sub == client_id, no sid.
        Map<String, Object> rawM2M = new LinkedHashMap<>();
        rawM2M.put("sub", "dnre-cadastro-api");
        rawM2M.put(ServiceAccountTokenClaims.CLAIM_CLIENT_ID, "dnre-cadastro-api");
        anonymousM2MJwt = new Jwt("raw-m2m-token", now, now.plusSeconds(60),
                Map.of("alg", "RS256"), rawM2M);
    }

    @Test
    void serviceAccountTokenIsSkipped() {
        IgrpOidcUser principal = wrapAsOidcUser(serviceAccountJwt, "00000000-0000-0000-0000-000000000099");
        OidcContextAuthenticationToken auth =
                new OidcContextAuthenticationToken(principal, serviceAccountJwt, List.of());

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService, never())
                .resolveOrEnrichAsync(any(), any(), any(), any(), any());
    }

    @Test
    void rawClientCredentialsTokenWithoutPrincipalTypeIsSkipped() {
        IgrpOidcUser principal = wrapAsOidcUser(anonymousM2MJwt, "dnre-cadastro-api");
        OidcContextAuthenticationToken auth =
                new OidcContextAuthenticationToken(principal, anonymousM2MJwt, List.of());

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService, never())
                .resolveOrEnrichAsync(any(), any(), any(), any(), any());
    }

    @Test
    void realUserTokenIsEnriched() {
        IgrpOidcUser principal = wrapAsOidcUser(userJwt, "00000000-0000-0000-0000-000000000123");
        OidcContextAuthenticationToken auth =
                new OidcContextAuthenticationToken(principal, userJwt, List.of());

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService)
                .resolveOrEnrichAsync(eq("00000000-0000-0000-0000-000000000123"),
                        any(), any(), any(), any());
    }

    @Test
    void nonOidcPrincipalIsSkipped() {
        var auth = new UsernamePasswordAuthenticationToken("anyone", null,
                List.of(new SimpleGrantedAuthority("ROLE_X")));

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService, never())
                .resolveOrEnrichAsync(any(), any(), any(), any(), any());
    }

    private static IgrpOidcUser wrapAsOidcUser(Jwt jwt, String sub) {
        OidcIdToken idToken = new OidcIdToken(jwt.getTokenValue(),
                jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
        UserProfile profile = new UserProfile(sub, "test-iss", "", "", "", "", "pwd", List.of());
        return new IgrpOidcUser(List.of(), idToken, profile);
    }

    // Mockito ArgumentMatchers shortcut imports
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
    private static <T> T eq(T value) { return org.mockito.ArgumentMatchers.eq(value); }
}
