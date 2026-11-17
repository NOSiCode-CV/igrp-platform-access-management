package cv.igrp.platform.access_management.oauth_server.infrastructure.scheduler;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.RefreshTokenTombstoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenTombstoneCleanupTest {

    private final RefreshTokenTombstoneRepository tombstones = mock(RefreshTokenTombstoneRepository.class);

    @Test
    void purgesTheExpiredTombstones() {
        when(tombstones.deleteExpired(any(Instant.class))).thenReturn(3, 0);
        RefreshTokenTombstoneCleanup cleanup = new RefreshTokenTombstoneCleanup(tombstones);

        cleanup.purgeExpired();
        cleanup.purgeExpired();

        verify(tombstones, org.mockito.Mockito.times(2)).deleteExpired(any(Instant.class));
    }

    @Test
    void aDatabaseFailureIsLoggedNotThrown() {
        when(tombstones.deleteExpired(any(Instant.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatCode(() -> new RefreshTokenTombstoneCleanup(tombstones).purgeExpired()).doesNotThrowAnyException();
    }
}
