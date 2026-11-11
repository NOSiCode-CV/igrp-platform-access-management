package cv.igrp.platform.access_management.authorization.infrastructure.adapter;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.authorization.domain.service.DatabaseAuthorizeApiAdapter;
import cv.igrp.platform.access_management.authorization.domain.service.PermissionCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link DatabaseAuthorizeApiAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseAuthorizeApiAdapterTest {

    @Mock
    private PermissionCheckService permissionCheckService;

    @InjectMocks
    private DatabaseAuthorizeApiAdapter adapter;

    private PermissionCheckRequest request;

    @BeforeEach
    void setUp() {
        request = new PermissionCheckRequest();
        request.setAction("read");
        request.setResource("document");
    }

    @Test
    void testCheck_ShouldReturnValidPermissionResponse() throws Exception {
        PermissionCacheEntryDTO cacheEntry = new PermissionCacheEntryDTO(true, List.of("ADMIN"), "Access granted");
        when(permissionCheckService.getOrLoadPermission(request)).thenReturn(cacheEntry);

        CompletableFuture<PermissionCheckResponse> future = adapter.check(request);
        PermissionCheckResponse response = future.get();

        assertNotNull(response);
        assertTrue(response.isAllowed());
        assertEquals(List.of("ADMIN"), response.getViaRoles());
        assertEquals("Access granted", response.getReason());
        assertFalse(response.isCacheHit(), "no per-check cache since #18");
        assertTrue(response.getResolutionTimeMs() >= 0);

        verify(permissionCheckService).getOrLoadPermission(request);
    }

    @Test
    void testCheck_WhenPermissionDenied_ShouldReturnDeniedResponse() throws Exception {
        PermissionCacheEntryDTO cacheEntry = new PermissionCacheEntryDTO(false, List.of("USER"), "Access denied");
        when(permissionCheckService.getOrLoadPermission(request)).thenReturn(cacheEntry);

        PermissionCheckResponse response = adapter.check(request).get();

        assertNotNull(response);
        assertFalse(response.isAllowed());
        assertEquals(List.of("USER"), response.getViaRoles());
        assertEquals("Access denied", response.getReason());
        assertFalse(response.isCacheHit());

        verify(permissionCheckService).getOrLoadPermission(request);
    }

    @Test
    void testCheck_WhenExceptionOccurs_ShouldPropagateException() {
        when(permissionCheckService.getOrLoadPermission(request))
                .thenThrow(new RuntimeException("Cache service failure"));

        CompletableFuture<PermissionCheckResponse> future = adapter.check(request);

        assertThrows(Exception.class, future::get);
        verify(permissionCheckService).getOrLoadPermission(request);
    }
}