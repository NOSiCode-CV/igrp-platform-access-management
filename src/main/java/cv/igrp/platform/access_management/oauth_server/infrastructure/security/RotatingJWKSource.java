package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * OWASP A02 — Multi-key JWK source that supports zero-downtime RSA key rotation.
 *
 * <p>Holds a <em>primary</em> signing key and an optional <em>secondary</em> key.
 * Both keys are published in the JWKS endpoint so resource servers can verify
 * tokens signed by either key during a rotation window.  The primary key (highest
 * {@code kid} / most recent) is used for <em>signing</em> new tokens; the secondary
 * key is retained only for <em>verification</em> until all tokens it signed have
 * expired.
 *
 * <h3>Rotation procedure</h3>
 * <ol>
 *   <li>Generate a new RSA key pair and configure it as the new
 *       {@code IGRP_OAUTH_SECONDARY_*} env vars while keeping the old pair as
 *       the primary.</li>
 *   <li>Restart the service. Both keys appear in JWKS; resource servers cache
 *       the new set.</li>
 *   <li>Swap primary ↔ secondary (promote new key to primary). The old key is
 *       now the secondary — still published for verification.</li>
 *   <li>Once all tokens signed with the old key have expired, remove the
 *       secondary configuration and restart.</li>
 * </ol>
 *
 * <p>The {@code kid} on each key must be unique and stable across restarts.
 * Configure via:
 * <ul>
 *   <li>{@code IGRP_OAUTH_KEY_ID} — kid for the primary key (required)</li>
 *   <li>{@code IGRP_OAUTH_SECONDARY_KEY_ID} — kid for the secondary key (optional)</li>
 *   <li>{@code IGRP_OAUTH_SECONDARY_PUBLIC_KEY} / {@code IGRP_OAUTH_SECONDARY_PRIVATE_KEY}
 *       — PEM paths for the secondary pair (optional)</li>
 * </ul>
 */
public class RotatingJWKSource implements JWKSource<SecurityContext> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RotatingJWKSource.class);

    /**
     * Combined JWK set: primary key first (used for signing), followed by any
     * secondary keys (verification only, retained during rotation window).
     */
    private final JWKSet jwkSet;

    /**
     * Creates a source with only the primary signing key.
     *
     * @param primaryKey the active signing RSA key
     */
    public RotatingJWKSource(RSAKey primaryKey) {
        this.jwkSet = new JWKSet(primaryKey);
        LOGGER.info("JWK source initialised with primary key kid={}",
                primaryKey.getKeyID());
    }

    /**
     * Creates a source with a primary signing key and one secondary key that
     * remains published for verification during the rotation window.
     *
     * @param primaryKey   the active signing RSA key
     * @param secondaryKey the retiring RSA key kept for JWT verification only
     */
    public RotatingJWKSource(RSAKey primaryKey, RSAKey secondaryKey) {
        this.jwkSet = new JWKSet(List.of(primaryKey, secondaryKey));
        LOGGER.info("JWK source initialised with primary key kid={} and secondary key kid={} (rotation mode)",
                primaryKey.getKeyID(), secondaryKey.getKeyID());
    }

    /**
     * Returns the matching keys from the JWK set.
     *
     * <p>Nimbus calls this method during JWT signing (needs the private key) and
     * JWKS endpoint serving (needs all public keys).  The selector handles both
     * cases: for signing it matches by {@code kid}; for the JWKS endpoint it
     * returns all keys.
     */
    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) throws KeySourceException {
        return selector.select(jwkSet);
    }

    /**
     * Returns the public JWK set suitable for advertising via the JWKS endpoint.
     * Private key material is stripped by Nimbus's {@link JWKSet#toPublicJWKSet()}.
     */
    public JWKSet getPublicJWKSet() {
        return jwkSet.toPublicJWKSet();
    }
}
