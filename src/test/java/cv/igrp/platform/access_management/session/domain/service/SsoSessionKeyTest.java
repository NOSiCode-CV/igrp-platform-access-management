package cv.igrp.platform.access_management.session.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R1.2, R1.3 and N3 — the properties the cascade depends on.
 */
class SsoSessionKeyTest {

    @Test
    @DisplayName("Login ids are unique per sign-in")
    void loginIdsAreUnique() {
        // R1.2: a different sign-in must yield a different key, or logout would
        // cascade across unrelated sign-ins.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertTrue(seen.add(SsoSessionKey.newLoginId()), "login ids must not repeat");
        }
    }

    @Test
    @DisplayName("Login ids carry 128 bits and are URL-safe")
    void loginIdsAre128BitsUrlSafe() {
        String id = SsoSessionKey.newLoginId();
        // 16 bytes base64url without padding = 22 characters.
        assertEquals(22, id.length());
        assertTrue(id.matches("[A-Za-z0-9_-]+"),
                "must be base64url: the value travels through JSON and URLs, got " + id);
    }

    @Test
    @DisplayName("The stored key is a digest, never the login id itself")
    void keyIsADigestNotThePlaintext() {
        String loginId = SsoSessionKey.newLoginId();
        String key = SsoSessionKey.hash(loginId);

        assertNotNull(key);
        // This is the whole point of R1.3. The login id appears in an
        // authentication principal; the key sits in a database column. If the
        // column held the id, a leak of the session table would hand over values
        // that identify live sign-ins.
        assertNotEquals(loginId, key);
        assertFalse(key.contains(loginId));
        // SHA-256 as base64url without padding = 43 characters, inside the
        // column's 64.
        assertEquals(43, key.length());
        assertTrue(key.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    @DisplayName("Hashing is stable, so sibling rows of one sign-in match exactly")
    void hashingIsDeterministic() {
        String loginId = SsoSessionKey.newLoginId();
        assertEquals(SsoSessionKey.hash(loginId), SsoSessionKey.hash(loginId));
        assertNotEquals(SsoSessionKey.hash(loginId), SsoSessionKey.hash(SsoSessionKey.newLoginId()));
    }

    @Test
    @DisplayName("No login id means no key, which is ordinary rather than an error")
    void absentLoginIdYieldsNoKey() {
        // R1.5: client_credentials grants and principals rebuilt from a bearer
        // token have no sign-in behind them. They must produce null, not an
        // exception and not a hash of the empty string — a shared hash would
        // make every keyless session a sibling of every other.
        assertNull(SsoSessionKey.hash(null));
        assertNull(SsoSessionKey.hash(""));
        assertNull(SsoSessionKey.hash("   "));
    }

    @Test
    @DisplayName("Fingerprints are short and never reveal the key")
    void fingerprintIsBounded() {
        // N3: a whole key correlates a user's entire sign-in across every
        // application they opened, so a log aggregator must never receive one.
        String key = SsoSessionKey.hash(SsoSessionKey.newLoginId());

        String fingerprint = SsoSessionKey.fingerprint(key);

        assertEquals(8, fingerprint.length());
        assertTrue(key.startsWith(fingerprint));
        assertEquals("none", SsoSessionKey.fingerprint(null));
        assertEquals("none", SsoSessionKey.fingerprint(""));
    }
}
