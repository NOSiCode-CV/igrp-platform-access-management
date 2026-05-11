package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Tombstone for refresh tokens that have been rotated out of an
 * {@link org.springframework.security.oauth2.server.authorization.OAuth2Authorization}.
 *
 * <p>Spring Authorization Server's
 * {@link org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService}
 * overwrites the previous refresh-token value in place when rotation is enabled
 * ({@code reuseRefreshTokens(false)}). Once overwritten there is no native way
 * to recognise a replay attempt of the OLD value — the lookup simply returns
 * {@code null} and the provider yields {@code invalid_grant}, with no chance
 * to react.
 *
 * <p>FR-8 (see {@code _specs/session/requirements.md}) requires that the
 * linked {@link cv.igrp.platform.access_management.session.infrastructure.persistence.entity.SessionEntity}
 * is revoked and a {@code SessionRevokedEvent} is published on every detected
 * replay. To satisfy that we capture the SHA-256 hash of the previous refresh
 * token at rotation time and consult that table on every {@code findByToken}
 * miss against the refresh-token endpoint.
 *
 * <p>Rows are short-lived: {@code expiresAt} matches the original refresh
 * token's TTL, after which the periodic cleanup scheduler deletes them.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_refresh_token_tombstone",
        indexes = {
                @Index(name = "ix_rtt_expires_at", columnList = "expires_at"),
                @Index(name = "ix_rtt_session_id", columnList = "session_id")
        })
public class RefreshTokenTombstoneEntity {

    /** SHA-256 hash (Base64-URL, no padding) of the rotated refresh-token value. */
    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    /** Session this refresh token was bound to ({@code SessionEntity.sessionId}). */
    @Column(name = "session_id")
    private UUID sessionId;

    /** Internal IGRP user id ({@code SessionEntity.userId}). May be null for M2M. */
    @Column(name = "user_id")
    private Integer userId;

    /** When the token was rotated out. */
    @Column(name = "invalidated_at", nullable = false)
    private Instant invalidatedAt;

    /** Original token expiry — after this point the row is safe to purge. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
