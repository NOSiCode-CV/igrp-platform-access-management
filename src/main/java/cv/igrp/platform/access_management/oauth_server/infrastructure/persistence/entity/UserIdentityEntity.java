package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Maps an external identity provider subject (e.g. Keycloak / WSO2 / Google)
 * to an internal {@link IGRPUserEntity}.
 *
 * <p>Unique by ({@code provider}, {@code user_id}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "t_user_identity",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_identity_provider_sub",
                columnNames = {"provider", "user_id"}))
public class UserIdentityEntity {

    @Id
    @Column(name = "id", unique = true, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 80)
    private String provider;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "connection", length = 120)
    private String connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igrp_user_id", nullable = false)
    private IGRPUserEntity user;
}
