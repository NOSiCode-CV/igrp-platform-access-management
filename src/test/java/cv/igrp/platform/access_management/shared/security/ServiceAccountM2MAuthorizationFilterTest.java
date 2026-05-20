package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCacheEntryDTO;
import cv.igrp.platform.access_management.authorization.domain.service.PermissionCacheService;
import cv.igrp.platform.access_management.shared.infrastructure.authorization.permission.M2MPermissions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServiceAccountM2MAuthorizationFilterTest {

    private PermissionCacheService permissionCacheService;
    private ServiceAccountM2MAuthorizationFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        permissionCacheService = mock(PermissionCacheService.class);
        filter = new ServiceAccountM2MAuthorizationFilter(permissionCacheService);
        filterChain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceAccountWithRequiredM2MPermissionPasses() throws ServletException, IOException {
        authenticate(serviceAccountJwt());
        when(permissionCacheService.getOrLoadPermission(any(PermissionCheckRequest.class)))
                .thenReturn(new PermissionCacheEntryDTO(true, List.of("M2M_SYNC"), "ok"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/m2m/sync/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);

        ArgumentCaptor<PermissionCheckRequest> captor = ArgumentCaptor.forClass(PermissionCheckRequest.class);
        verify(permissionCacheService).getOrLoadPermission(captor.capture());
        assertEquals(M2MPermissions.IGRP_M2M_SYNC, captor.getValue().getAction());
        assertEquals("00000000-0000-0000-0000-000000000123", captor.getValue().getSubject());
    }

    @Test
    void serviceAccountWithoutRequiredPermissionIsForbidden() throws ServletException, IOException {
        authenticate(serviceAccountJwt());
        when(permissionCacheService.getOrLoadPermission(any(PermissionCheckRequest.class)))
                .thenReturn(new PermissionCacheEntryDTO(false, List.of(), "denied"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/m2m/sync/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void legacyStaticM2MAuthenticationSkipsServiceAccountPermissionFilter()
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/m2m/sync/resources");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(permissionCacheService);
    }

    private static Jwt serviceAccountJwt() {
        Instant now = Instant.now();
        return new Jwt(
                "service-account-token",
                now,
                now.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "00000000-0000-0000-0000-000000000123",
                        ServiceAccountTokenClaims.CLAIM_PRINCIPAL_TYPE,
                        ServiceAccountTokenClaims.PRINCIPAL_TYPE_SERVICE_ACCOUNT
                ));
    }

    private static void authenticate(Jwt jwt) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
    }
}
