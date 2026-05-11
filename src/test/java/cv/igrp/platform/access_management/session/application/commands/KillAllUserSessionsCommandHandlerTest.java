package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.platform.access_management.session.domain.service.SessionInvalidationService;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KillAllUserSessionsCommandHandlerTest {

    @Mock
    private SessionInvalidationService sessionInvalidationService;
    @Mock
    private IGRPUserEntityRepository userRepository;

    @InjectMocks
    private KillAllUserSessionsCommandHandler handler;

    @Test
    void delegatesToInvalidationServiceWithInternalId_whenUserExists() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(77);
        user.setExternalId("ext-77");
        when(userRepository.findByExternalId("ext-77")).thenReturn(Optional.of(user));

        Boolean result = handler.handle(
                new KillAllUserSessionsCommand("ext-77", "ADMIN_LOGOUT_ALL", "admin"));

        assertTrue(result);
        verify(sessionInvalidationService).invalidateUserSession(77, "ADMIN_LOGOUT_ALL");
    }

    @Test
    void returnsFalse_whenUserMissing() {
        when(userRepository.findByExternalId("missing")).thenReturn(Optional.empty());

        Boolean result = handler.handle(
                new KillAllUserSessionsCommand("missing", "ADMIN_LOGOUT_ALL", "admin"));

        assertFalse(result);
        verify(sessionInvalidationService, never()).invalidateUserSession(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
