package cv.igrp.platform.access_management.shared.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.security.UserAuthorizations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** #33: a self-service caller is accepted only while signed in. */
@ExtendWith(MockitoExtension.class)
class SignedInGuardTest {

    private static final String USER = "00000000-0000-0000-0000-0000000000a1";

    @Mock
    private UserAuthorizations authorizations;

    private JwtAuthenticationToken token(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claims(c -> c.putAll(claims)).build();
        return new JwtAuthenticationToken(jwt, List.of());
    }

    @Test
    void aSignedInUser() {
        when(authorizations.hasActive(USER)).thenReturn(true);

        assertThat(new SignedInGuard(authorizations).check(token(Map.of("sub", USER))).isGranted()).isTrue();
    }

    @Test
    void aSignedOutUserIs401NotForbidden() {
        when(authorizations.hasActive(USER)).thenReturn(false);
        SignedInGuard guard = new SignedInGuard(authorizations);

        assertThatThrownBy(() -> guard.check(token(Map.of("sub", USER))))
                .isInstanceOf(CredentialsExpiredException.class);
    }

    @Test
    void aServiceAccountHasNoSignIn() {
        assertThat(new SignedInGuard(authorizations).check(token(Map.of("sub", "client",
                ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE, ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT)))
                .isGranted()).isTrue();
        verifyNoInteractions(authorizations);
    }

    @Test
    void aSubjectThatIsNotAUserIsNotSignedIn() {
        assertThat(new SignedInGuard(authorizations).isSignedIn(token(Map.of("sub", "not-a-uuid")))).isFalse();
    }

    @Test
    void noTokenIsDeniedNotThrown() {
        SignedInGuard guard = new SignedInGuard(authorizations);

        assertThat(guard.check(null).isGranted()).isFalse();
        assertThat(guard.check(new TestingAuthenticationToken("someone", "pw")).isGranted()).isFalse();
        verifyNoInteractions(authorizations);
    }
}
