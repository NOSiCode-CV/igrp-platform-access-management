package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OidcLoginFailureAnalysisTest {

    private static final Instant NOW = Instant.parse("2026-09-01T11:00:00Z");

    private static AuthenticationException oauthFailure(String code, String description) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, description, null), description);
    }

    @Test
    void notYetValidToken_isClassifiedAsClockSkew_withLocalClockBehind() {
        // The reported production symptom.
        AuthenticationException ex = oauthFailure("invalid_id_token",
                "An error occurred while attempting to decode the Jwt: Jwt used before 2026-09-01T11:28:18Z");

        OidcLoginFailureAnalysis analysis = OidcLoginFailureAnalysis.of(ex, NOW);

        assertTrue(analysis.isClockSkew());
        assertEquals(OidcLoginFailureAnalysis.CLOCK_SKEW_CODE, analysis.errorCode());
        assertEquals(OidcLoginFailureAnalysis.Direction.LOCAL_CLOCK_BEHIND, analysis.direction());
        assertEquals(28L * 60 + 18, analysis.skew().toSeconds());
    }

    @Test
    void expiredToken_isClassifiedAsClockSkew_withLocalClockAhead() {
        AuthenticationException ex = oauthFailure("invalid_id_token",
                "An error occurred while attempting to decode the Jwt: Jwt expired at 2026-09-01T10:30:00Z");

        OidcLoginFailureAnalysis analysis = OidcLoginFailureAnalysis.of(ex, NOW);

        assertTrue(analysis.isClockSkew());
        assertEquals(OidcLoginFailureAnalysis.Direction.LOCAL_CLOCK_AHEAD_OR_TOKEN_STALE, analysis.direction());
        assertEquals(30L * 60, analysis.skew().toSeconds());
    }

    @Test
    void otherOauthFailures_keepTheirOwnCode() {
        // An issuer mismatch must NOT be reported as a clock problem.
        AuthenticationException ex = oauthFailure("invalid_id_token",
                "The iss claim is not valid");

        OidcLoginFailureAnalysis analysis = OidcLoginFailureAnalysis.of(ex, NOW);

        assertFalse(analysis.isClockSkew());
        assertEquals("invalid_id_token", analysis.errorCode());
        assertEquals(OidcLoginFailureAnalysis.Direction.NONE, analysis.direction());
        assertNull(analysis.skew());
    }

    @Test
    void nonOauthException_fallsBackToLoginFailed() {
        OidcLoginFailureAnalysis analysis =
                OidcLoginFailureAnalysis.of(new BadCredentialsException("nope"), NOW);

        assertEquals(OidcLoginFailureAnalysis.DEFAULT_CODE, analysis.errorCode());
        assertNull(analysis.rawDescription());
        assertNull(analysis.skew());
    }

    @Test
    void unparsableTimestamp_doesNotClaimAClockProblem() {
        AuthenticationException ex = oauthFailure("invalid_id_token", "Jwt used before soon-ish");

        OidcLoginFailureAnalysis analysis = OidcLoginFailureAnalysis.of(ex, NOW);

        assertFalse(analysis.isClockSkew());
        assertEquals("invalid_id_token", analysis.errorCode());
    }

    @Test
    void skewSummary_isSafeWhenThereIsNoSkew() {
        OidcLoginFailureAnalysis analysis =
                OidcLoginFailureAnalysis.of(new BadCredentialsException("nope"), NOW);

        assertNotNull(analysis.skewSummary());
    }
}
