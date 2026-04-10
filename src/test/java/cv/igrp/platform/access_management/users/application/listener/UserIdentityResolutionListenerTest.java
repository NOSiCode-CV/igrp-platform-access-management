package cv.igrp.platform.access_management.users.application.listener;

import cv.igrp.platform.access_management.shared.security.IgrpOidcUser;
import cv.igrp.platform.access_management.shared.security.OidcContextAuthenticationToken;
import cv.igrp.platform.access_management.shared.security.UserProfile;
import cv.igrp.platform.access_management.users.application.service.UserIdentityResolutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserIdentityResolutionListenerTest {

    @Mock
    private UserIdentityResolutionService identityResolutionService;

    @InjectMocks
    private UserIdentityResolutionListener listener;

    @Test
    void listener_calls_resolve_or_enrich_async_for_oidc_user() {
        UserProfile profile = new UserProfile(
                "ext-id", 
                "issuer", 
                "Test User", 
                "user@example.com", 
                "+2391234567", 
                "12345678A001B", 
                "pwd", 
                Collections.emptyList()
        );

        IgrpOidcUser oidcUser = mock(IgrpOidcUser.class);
        when(oidcUser.getUserProfile()).thenReturn(profile);

        Jwt jwt = mock(Jwt.class);
        OidcContextAuthenticationToken auth = new OidcContextAuthenticationToken(oidcUser, jwt, Collections.emptyList());

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService, times(1)).resolveOrEnrichAsync(
                eq("ext-id"), 
                eq("user@example.com"), 
                eq("12345678A001B"), 
                eq("+2391234567"), 
                eq("Test User")
        );
    }

    @Test
    void listener_ignores_non_oidc_principal() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", "pass");

        listener.onApplicationEvent(new AuthenticationSuccessEvent(auth));

        verify(identityResolutionService, never()).resolveOrEnrichAsync(any(), any(), any(), any(), any());
    }
}
