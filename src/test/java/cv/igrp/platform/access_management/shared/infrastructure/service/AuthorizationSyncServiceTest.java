package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.m2m.domain.service.PermissionSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationSyncServiceTest {

    @Mock private PermissionSyncService permissionSyncService;

    @InjectMocks
    private AuthorizationSyncService service;

    @Test
    void syncAuthorization_DelegatesToSyncService() {
        service.syncAuthorization();
        verify(permissionSyncService).synchronizePermissions(anyList(), eq(true));
    }

    @Test
    void syncAuthorization_SwallowsException() {
        doThrow(new RuntimeException("boom")).when(permissionSyncService)
                .synchronizePermissions(anyList(), eq(true));
        // Must not propagate
        service.syncAuthorization();
        verify(permissionSyncService).synchronizePermissions(anyList(), eq(true));
    }
}
