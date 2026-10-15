package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The inbound {@code id_token_hint} must never reach the logs verbatim. It is a
 * replayable logout credential carrying the subject's identity claims, and
 * INFO-level logs are shipped, indexed and retained well beyond the debugging
 * need that originally justified printing it.
 */
class SessionLogoutRedactionTest {

    private static final String SECRET_SUB = "00000000-0000-0000-0000-000000000042";

    private OidcIdToken token(String value) {
        return new OidcIdToken(value, Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", SECRET_SUB));
    }

    @Test
    @DisplayName("the token value never appears in the rendered description")
    void doesNotDiscloseTheToken() {
        String value = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzZWNyZXQifQ.c2lnbmF0dXJl";

        String rendered = SessionLogoutHandler.describeIdToken(token(value));

        assertFalse(rendered.contains(value), "full token leaked: " + rendered);
        assertFalse(rendered.contains("eyJ"), "a JWT segment leaked: " + rendered);
        assertFalse(rendered.contains(SECRET_SUB), "a claim leaked: " + rendered);
    }

    @Test
    @DisplayName("the fingerprint is stable, so two attempts can be compared")
    void sameTokenYieldsSameFingerprint() {
        String value = "eyJhbGciOiJSUzI1NiJ9.payload.sig";

        assertEquals(SessionLogoutHandler.describeIdToken(token(value)),
                SessionLogoutHandler.describeIdToken(token(value)));
    }

    @Test
    @DisplayName("different hints are distinguishable — that is the diagnostic value")
    void differentTokensYieldDifferentFingerprints() {
        assertNotEquals(SessionLogoutHandler.describeIdToken(token("eyJhbGciOiJSUzI1NiJ9.a.sig")),
                SessionLogoutHandler.describeIdToken(token("eyJhbGciOiJSUzI1NiJ9.b.sig")));
    }

    @Test
    @DisplayName("length is retained, since 'was a hint sent at all' is the first question")
    void retainsLength() {
        String value = "eyJhbGciOiJSUzI1NiJ9.payload.sig";

        assertTrue(SessionLogoutHandler.describeIdToken(token(value)).contains("len=" + value.length()));
    }

    @Test
    @DisplayName("a missing hint renders as absent, not null or an empty string")
    void absentTokenIsExplicit() {
        assertEquals("<absent>", SessionLogoutHandler.describeIdToken(null));
    }
}
