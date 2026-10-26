package cv.igrp.platform.access_management.session.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The SSO session key — what links every application session opened under one
 * AM sign-in, so logout can revoke them together (R1.1–R1.3).
 *
 * <p>Two values, and the distinction matters:
 *
 * <ul>
 *   <li>the <b>login id</b>, 128 random bits minted once per browser sign-in and
 *       carried on the authentication principal;</li>
 *   <li>the <b>key</b>, its SHA-256 digest, which is what {@code t_user_session}
 *       stores.</li>
 * </ul>
 *
 * <p>Only the digest is persisted. The login id travels with the principal and,
 * through it, into the serialized {@code OAuth2Authorization} — so a leak of the
 * session table must not hand anyone a value that appears in an authentication
 * context. Hashing makes the stored column useless for impersonation while
 * remaining exact for equality, which is all the cascade needs.
 *
 * <p>Deliberately <b>not</b> the servlet session id. That would tie the key to
 * the servlet session, making a shared session store a correctness requirement
 * for logout rather than only for silent sign-in: on several replicas,
 * applications that authorized against different pods would get different keys
 * and the cascade would be silently partial. See R1.6 in
 * {@code plans/sso-session-logout-cascade/requirements.md}.
 */
public final class SsoSessionKey {

    /** 128 bits, per R1.6. */
    private static final int LOGIN_ID_BYTES = 16;

    /** How many characters of the key may appear in a log line (N3). */
    private static final int FINGERPRINT_LENGTH = 8;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SsoSessionKey() {
    }

    /**
     * Mint a login id for one browser sign-in. Called once per sign-in, not per
     * token issuance.
     */
    public static String newLoginId() {
        byte[] bytes = new byte[LOGIN_ID_BYTES];
        RANDOM.nextBytes(bytes);
        return encode(bytes);
    }

    /**
     * Derive the stored key from a login id.
     *
     * @return the SHA-256 digest, base64url without padding, or {@code null} when
     *         there is no login id — which is the normal case for grants with no
     *         browser sign-in behind them (R1.5), not an error.
     */
    public static String hash(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return encode(digest.digest(loginId.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE; unreachable.
            throw new IllegalStateException(e);
        }
    }

    /**
     * A short, non-reversible label for log lines. Never log a whole key: it is a
     * stable identifier for a user's sign-in across every application they
     * opened, so a log aggregator holding it can correlate their whole session
     * (N3).
     */
    public static String fingerprint(String key) {
        if (key == null || key.isBlank()) {
            return "none";
        }
        return key.length() <= FINGERPRINT_LENGTH ? key : key.substring(0, FINGERPRINT_LENGTH);
    }

    private static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
