package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * A user's OAuth2 authorizations in AM's store (#19). Deleting one invalidates
 * its refresh token, so no new access token can be minted from it.
 * <p>
 * Rows are deleted directly, not through the cascading
 * {@link OAuth2AuthorizationService}: its revocation cascade revokes the bound
 * session in a new transaction, which would wait forever on the lock of a kill
 * that has just revoked that session itself. Both callers revoke the sessions
 * themselves.
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
    private static final String BY_PRINCIPAL = "SELECT id FROM oauth2_authorization WHERE " + PRINCIPALS;
    private static final String DELETE_BY_PRINCIPAL = "DELETE FROM oauth2_authorization WHERE " + PRINCIPALS;
    private static final String DELETE_BY_ID = "DELETE FROM oauth2_authorization WHERE id = ?";

    private final JdbcTemplate jdbc;
    private final OAuth2AuthorizationService authorizations;

    public UserAuthorizations(JdbcTemplate jdbc, OAuth2AuthorizationService authorizations) {
        this.jdbc = jdbc;
        this.authorizations = authorizations;
    }

    /** Deletes every authorization of the user, on every device, in the caller's transaction: a kill. */
    public int removeAll(String userId) {
        return jdbc.update(DELETE_BY_PRINCIPAL, userId, userId);
    }

    /**
     * Deletes the authorizations whose access token belongs to one of the
     * sessions: a logout of one sign-in. In its own transaction, so a failure
     * here never rolls back the logout itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int removeForSessions(String userId, Set<String> sessionIds) {
        int removed = 0;
        for (String id : jdbc.queryForList(BY_PRINCIPAL, String.class, userId, userId)) {
            OAuth2Authorization authorization = authorizations.findById(id);
            if (authorization != null && sessionIds.contains(sid(authorization))) {
                removed += jdbc.update(DELETE_BY_ID, id);
            }
        }
        return removed;
    }

    private static String sid(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<?> access = authorization.getAccessToken();
        Map<String, Object> claims = access != null ? access.getClaims() : null;
        Object sid = claims != null ? claims.get("sid") : null;
        return sid != null ? sid.toString() : null;
    }
}
