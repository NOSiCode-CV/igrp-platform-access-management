package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * A user's OAuth2 authorizations in AM's store (#19, #33). A user is signed in while they hold
 * an active one; deleting one invalidates its refresh token, so no new access token can be
 * minted from it. Rows are read and deleted directly, in the caller's transaction.
 */
@Component
public class UserAuthorizations {

    /**
     * The user's principal names: AM's internal id, and the upstream IdP
     * subject of each federated identity, under which a federated sign-in's
     * authorization is stored.
     */
    private static final String PRINCIPALS = """
            principal_name = ?
               OR principal_name IN (SELECT user_id FROM t_user_identity WHERE igrp_user_id = ?)""";
    private static final String DELETE_BY_PRINCIPAL = "DELETE FROM oauth2_authorization WHERE " + PRINCIPALS;

    /** How Spring Authorization Server marks a revoked token in its metadata. */
    private static final String INVALIDATED = "%\"metadata.token.invalidated\":true%";

    /**
     * A token still usable: issued, not expired and not revoked. Times are
     * compared with a parameter from the JVM, which wrote them, not with the
     * database's {@code now()}.
     */
    private static final String ACTIVE = """
            SELECT EXISTS (SELECT 1 FROM oauth2_authorization WHERE (%s) AND (
                (refresh_token_value IS NOT NULL AND refresh_token_expires_at > ?
                    AND COALESCE(refresh_token_metadata, '') NOT LIKE ?)
                OR (access_token_value IS NOT NULL AND access_token_expires_at > ?
                    AND COALESCE(access_token_metadata, '') NOT LIKE ?)))""".formatted(PRINCIPALS);

    private final JdbcTemplate jdbc;

    public UserAuthorizations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Whether the user is signed in (#33, spec "Tokens"): they hold an
     * authorization whose refresh or access token is still usable.
     */
    public boolean hasActive(String userId) {
        Timestamp now = Timestamp.from(Instant.now());
        return Boolean.TRUE.equals(jdbc.queryForObject(ACTIVE, Boolean.class,
                userId, userId, now, INVALIDATED, now, INVALIDATED));
    }

    /** Deletes every authorization of the user, on every device, in the caller's transaction: a kill. */
    public int removeAll(String userId) {
        return jdbc.update(DELETE_BY_PRINCIPAL, userId, userId);
    }
}
