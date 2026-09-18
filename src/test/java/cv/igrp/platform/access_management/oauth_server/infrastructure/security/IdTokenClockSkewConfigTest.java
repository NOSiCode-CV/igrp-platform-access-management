package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The tolerance is a mitigation, so it must be bounded — a huge value would
 * silently disable expiry enforcement on the upstream ID Token.
 */
class IdTokenClockSkewConfigTest {

    @Test
    void usesConfiguredValueWithinRange() {
        assertEquals(Duration.ofSeconds(120),
                AuthorizationServerConfig.resolveIdTokenClockSkew(120));
    }

    @Test
    void defaultMatchesSpringsOwn() {
        assertEquals(Duration.ofSeconds(60),
                AuthorizationServerConfig.resolveIdTokenClockSkew(60));
    }

    @Test
    void clampsAboveCeiling() {
        assertEquals(Duration.ofSeconds(AuthorizationServerConfig.MAX_ID_TOKEN_CLOCK_SKEW_SECONDS),
                AuthorizationServerConfig.resolveIdTokenClockSkew(86_400));
    }

    @Test
    void negativeBecomesZero() {
        assertEquals(Duration.ZERO, AuthorizationServerConfig.resolveIdTokenClockSkew(-5));
    }

    @Test
    void zeroIsAllowed() {
        assertEquals(Duration.ZERO, AuthorizationServerConfig.resolveIdTokenClockSkew(0));
    }
}
