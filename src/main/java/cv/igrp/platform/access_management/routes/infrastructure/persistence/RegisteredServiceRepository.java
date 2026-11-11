package cv.igrp.platform.access_management.routes.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegisteredServiceRepository extends JpaRepository<RegisteredServiceEntity, String> {

    /** The service a client registers; one per client (unique index, V20_1). */
    Optional<RegisteredServiceEntity> findByOwnerClientId(String ownerClientId);
}
