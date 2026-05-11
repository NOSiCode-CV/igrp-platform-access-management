package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenTombstoneRepository extends JpaRepository<RefreshTokenTombstoneEntity, String> {

    Optional<RefreshTokenTombstoneEntity> findByTokenHash(String tokenHash);

    /**
     * Purge entries whose original refresh-token expiry is already in the past
     * — invoked by {@link cv.igrp.platform.access_management.session.infrastructure.scheduler.SessionCleanupScheduler}.
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenTombstoneEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
