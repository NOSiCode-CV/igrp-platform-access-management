package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.RefreshTokenTombstoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenTombstoneRepository extends JpaRepository<RefreshTokenTombstoneEntity, String> {

    Optional<RefreshTokenTombstoneEntity> findByTokenHash(String tokenHash);

    /**
     * Purge entries whose original refresh-token expiry is already in the past
     * — invoked by {@link cv.igrp.platform.access_management.session.infrastructure.scheduler.SessionCleanupScheduler}.
     *
     * <p>{@code @Transactional} is required because {@code @Modifying} alone
     * tells Spring Data "this is a write query"; it does NOT open a
     * transaction. Without it, Hibernate throws {@code TransactionRequiredException}
     * — which is exactly what the scheduler hit at 02:00 UTC.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshTokenTombstoneEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
