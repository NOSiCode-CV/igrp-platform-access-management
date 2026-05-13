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

    private static final String USER_ID = "00000000-0000-0000-0000-000000000077";

    @Mock
    private SessionInvalidationService sessionInvalidationService;
    @Mock
    private IGRPUserEntityRepository userRepository;

    @InjectMocks
    private KillAllUserSessionsCommandHandler handler;

    @Test
    void delegatesToInvalidationServiceWithInternalId_whenUserExists() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        Boolean result = handler.handle(
                new KillAllUserSessionsCommand(USER_ID, "ADMIN_LOGOUT_ALL", "admin"));

        assertTrue(result);
        verify(sessionInvalidationService).invalidateUserSession(USER_ID, "ADMIN_LOGOUT_ALL");
    }

    @Test
    void returnsFalse_whenUserMissing() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        Boolean result = handler.handle(
                new KillAllUserSessionsCommand("missing", "ADMIN_LOGOUT_ALL", "admin"));

        assertFalse(result);
        verify(sessionInvalidationService, never()).invalidateUserSession(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
