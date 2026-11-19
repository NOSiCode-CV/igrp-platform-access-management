package cv.igrp.platform.access_management.oauth_server.infrastructure.scheduler;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Purges refresh-token tombstones once their refresh token has expired: a replay of it is
 * refused anyway by then. It ran with the session cleanup until sessions were removed (#33).
 */
@Slf4j
@Component
public class RefreshTokenTombstoneCleanup {

    private final RefreshTokenTombstoneRepository tombstones;

    public RefreshTokenTombstoneCleanup(RefreshTokenTombstoneRepository tombstones) {
        this.tombstones = tombstones;
    }

    @Scheduled(fixedDelayString = "${igrp.oauth.tombstone-cleanup-interval-seconds:300}000")
    public void purgeExpired() {
        try {
            int purged = tombstones.deleteExpired(Instant.now());
            if (purged > 0) {
                log.info("Purged {} expired refresh-token tombstones", purged);
            }
        } catch (DataAccessException e) {
            log.warn("Error purging expired refresh-token tombstones: {}", e.getMessage());
        }
    }
}
