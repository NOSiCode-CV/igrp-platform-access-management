package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.UserIdentityJpaRepository;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v3-12 (#20): root may be created at startup under its email, before its
 * owner ever signs in. Their first federated login links to that row instead
 * of provisioning a second, non-root user.
 */
class IgrpOidcUserServiceRootTest {

    private final IGRPUserEntityRepository users = mock(IGRPUserEntityRepository.class);
    private IgrpOidcUserService service;

    @BeforeEach
    void service() {
        service = new IgrpOidcUserService(mock(UserIdentityJpaRepository.class), users, mock(SessionAuditLogger.class));
        ReflectionTestUtils.setField(service, "rootEmail", "Root@Example.cv");
    }

    @Test
    void theConfiguredRootsLoginFindsTheRootRow() {
        IGRPUserEntity root = new IGRPUserEntity();
        when(users.findByEmailIgnoreCase("root@example.cv")).thenReturn(Optional.of(root));

        assertThat(service.configuredRoot(" root@example.cv ")).containsSame(root);
    }

    @Test
    void anyOtherLoginIsProvisionedAsBefore() {
        assertThat(service.configuredRoot("someone@example.cv")).isEmpty();
        assertThat(service.configuredRoot(null)).isEmpty();
        verifyNoInteractions(users);
    }
}
