package cv.igrp.platform.access_management.authorization.domain.service;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase G3 — TEMPORARY users must be rejected by the permission check, the
 * same way DELETED / INACTIVE users already were.
 */
class PermissionCacheServiceTest {

    private JdbcTemplate jdbcTemplate;
    private IGRPUserEntityRepository userRepository;
    private PermissionCacheService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        userRepository = mock(IGRPUserEntityRepository.class);
        service = new PermissionCacheService(jdbcTemplate, userRepository);
    }

    @Test
    void temporaryUserFailsPermissionCheck() {
        assertCheckDenied(Status.TEMPORARY);
    }

    @Test
    void inactiveUserFailsPermissionCheck() {
        assertCheckDenied(Status.INACTIVE);
    }

    @Test
    void deletedUserFailsPermissionCheck() {
        assertCheckDenied(Status.DELETED);
    }

    private void assertCheckDenied(Status status) {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(11);
        user.setStatus(status);
        when(userRepository.findByIdWithRolesAndPermissions(eq(11))).thenReturn(Optional.of(user));
        // Defensive: ensure SQL fallback is never consulted for these tests
        when(jdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class),
                any(), any())).thenReturn(Collections.emptyList());

        PermissionCheckRequest req = new PermissionCheckRequest();
        req.setSubject("11");
        req.setAction("igrp.users.view");

        PermissionCacheEntryDTO dto = service.checkInternal(req);
        assertFalse(dto.allowed(), "Status=" + status + " must be denied");
    }
}
