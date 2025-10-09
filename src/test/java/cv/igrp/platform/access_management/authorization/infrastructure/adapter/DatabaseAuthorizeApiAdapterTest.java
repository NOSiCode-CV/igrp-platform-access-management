package cv.igrp.platform.access_management.authorization.infrastructure.adapter;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.authorization.domain.service.DatabaseAuthorizeApiAdapter;
import cv.igrp.platform.access_management.authorization.domain.service.PermissionCacheService;
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
    private PermissionCacheService permissionCacheService;

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
        when(permissionCacheService.getOrLoadPermission(request)).thenReturn(cacheEntry);
        when(permissionCacheService.isFromCache()).thenReturn(true);

        CompletableFuture<PermissionCheckResponse> future = adapter.check(request);
        PermissionCheckResponse response = future.get();

        assertNotNull(response);
        assertTrue(response.isAllowed());
        assertEquals(List.of("ADMIN"), response.getViaRoles());
        assertEquals("Access granted", response.getReason());
        assertTrue(response.isCacheHit());
        assertTrue(response.getResolutionTimeMs() >= 0);

        verify(permissionCacheService).setFromCacheAsTrue();
        verify(permissionCacheService).getOrLoadPermission(request);
        verify(permissionCacheService).isFromCache();
    }

    @Test
    void testCheck_WhenPermissionDenied_ShouldReturnDeniedResponse() throws Exception {
        PermissionCacheEntryDTO cacheEntry = new PermissionCacheEntryDTO(false, List.of("USER"), "Access denied");
        when(permissionCacheService.getOrLoadPermission(request)).thenReturn(cacheEntry);
        when(permissionCacheService.isFromCache()).thenReturn(false);

        PermissionCheckResponse response = adapter.check(request).get();

        assertNotNull(response);
        assertFalse(response.isAllowed());
        assertEquals(List.of("USER"), response.getViaRoles());
        assertEquals("Access denied", response.getReason());
        assertFalse(response.isCacheHit());

        verify(permissionCacheService).setFromCacheAsTrue();
        verify(permissionCacheService).getOrLoadPermission(request);
    }

    @Test
    void testCheck_WhenExceptionOccurs_ShouldPropagateException() {
        when(permissionCacheService.getOrLoadPermission(request))
                .thenThrow(new RuntimeException("Cache service failure"));

        CompletableFuture<PermissionCheckResponse> future = adapter.check(request);

        assertThrows(Exception.class, future::get);
        verify(permissionCacheService).setFromCacheAsTrue();
        verify(permissionCacheService).getOrLoadPermission(request);
    }
}