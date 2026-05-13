package cv.igrp.platform.access_management.users.application.service;

import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase G3 — first-login provisioning must write {@link Status#TEMPORARY}.
 */
@ExtendWith(MockitoExtension.class)
class UserIdentityResolutionServiceTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private SessionAuditLogger sessionAuditLogger;

    @InjectMocks
    private UserIdentityResolutionService service;

    @Test
    void firstLoginCreatesTemporaryUser() {
        String uid = "00000000-0000-0000-0000-000000000042";
        when(userRepository.findByUsername("ext-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        when(userRepository.save(any(IGRPUserEntity.class))).thenAnswer(inv -> {
            IGRPUserEntity u = inv.getArgument(0);
            u.setId(uid);
            return u;
        });

        IGRPUserEntity created = service.resolveOrCreate("ext-1", "u@example.com",
                null, null, "User One");

        ArgumentCaptor<IGRPUserEntity> captor = ArgumentCaptor.forClass(IGRPUserEntity.class);
        verify(userRepository).save(captor.capture());
        assertEquals(Status.TEMPORARY, captor.getValue().getStatus());
        assertEquals(Status.TEMPORARY, created.getStatus());
        verify(sessionAuditLogger).recordUserStatusTransitioned(
                org.mockito.ArgumentMatchers.eq(uid),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("TEMPORARY"),
                org.mockito.ArgumentMatchers.eq(SessionAuditLogger.SYSTEM),
                org.mockito.ArgumentMatchers.eq("FIRST_LOGIN"));
    }
}
