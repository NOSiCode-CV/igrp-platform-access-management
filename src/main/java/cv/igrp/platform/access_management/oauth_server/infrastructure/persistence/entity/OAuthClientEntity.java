package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 client persisted in the platform database. Each client is owned by a
 * single {@link ApplicationEntity} (1 application → N clients) so that tokens
 * issued by the authorization server can be linked back to the calling app /
 * resource.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_oauth_client", indexes = {
        @Index(name = "idx_oauth_client_client_id", columnList = "client_id", unique = true)
})
public class OAuthClientEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "client_id", unique = true, nullable = false, length = 120)
    private String clientId;

    @Column(name = "client_secret", nullable = false)
    private String clientSecret;

    @Column(name = "client_name", length = 180)
    private String clientName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * When {@code true}, the authorization server requires a PKCE
     * {@code code_challenge} / {@code code_verifier} pair on every
     * {@code authorization_code} flow for this client (RFC 7636).
     *
     * <p>Defaults to {@code true} so newly created clients are secure
     * by default. Set to {@code false} only for legacy confidential
     * server-side clients that cannot be updated to send PKCE yet.
     */
    /*
     * Declared as java.lang.Boolean (object) rather than primitive boolean so
     * Hibernate can hydrate the entity from rows where the column is NULL
     * (e.g. legacy rows from before V8 backfilled `require_pkce`). The
     * isRequirePkce() accessor below normalizes null -> true so callers still
     * see the entity-level default.
     */
    @Column(name = "require_pkce", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean requirePkce = Boolean.TRUE;

    public boolean isRequirePkce() {
        return requirePkce == null || requirePkce;
    }

    @Column(name = "access_token_ttl", nullable = false)
    private int accessTokenTtl;

    @Column(name = "refresh_token_ttl", nullable = false)
    private int refreshTokenTtl;

    @Column(name = "authorization_code_ttl", nullable = false)
    private int authorizationCodeTtl;

    /**
     * Owning application / resource. Each client belongs to exactly one app;
     * an app may have many clients.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private ApplicationEntity application;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_oauth_client_scope", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "scope")
    private Set<String> scopes = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_oauth_client_redirect_uri", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "redirect_uri")
    private Set<String> redirectUris = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_oauth_client_post_logout_redirect_uri", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "post_logout_redirect_uri")
    private Set<String> postLogoutRedirectUris = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_oauth_client_grant_type", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "grant_type")
    private Set<String> grantTypes = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
