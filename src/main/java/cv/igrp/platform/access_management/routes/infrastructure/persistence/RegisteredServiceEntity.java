package cv.igrp.platform.access_management.routes.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A service that has registered its routes, bound to the client that registered
 * it first (v3-02). Another client registering under the same name is refused;
 * #11 turns "known client, new name" into a rename.
 */
@Entity
@Table(name = "t_registered_service")
public class RegisteredServiceEntity {

    @Id
    @Column(name = "service", length = 120)
    private String service;

    @Column(name = "owner_client_id", length = 120, nullable = false, updatable = false, unique = true)
    private String ownerClientId;

    @Column(name = "last_version", length = 64, nullable = false)
    private String lastVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_registered_at", nullable = false)
    private Instant lastRegisteredAt;

    protected RegisteredServiceEntity() {
    }

    public RegisteredServiceEntity(String service, String ownerClientId, String version, Instant now) {
        this.service = service;
        this.ownerClientId = ownerClientId;
        this.lastVersion = version;
        this.createdAt = now;
        this.lastRegisteredAt = now;
    }

    public void registered(String version, Instant now) {
        this.lastVersion = version;
        this.lastRegisteredAt = now;
    }

    public String getService() {
        return service;
    }

    public String getOwnerClientId() {
        return ownerClientId;
    }

    public String getLastVersion() {
        return lastVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastRegisteredAt() {
        return lastRegisteredAt;
    }
}
