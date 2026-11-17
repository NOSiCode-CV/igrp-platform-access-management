package cv.igrp.platform.access_management.oauth_server.infrastructure.bootstrap;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** v3-25 (#33): the default client is the one every frontend shares. */
class DefaultOAuthClientBootstrapTest {

    private final OAuthClientJpaRepository repository = mock(OAuthClientJpaRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    @DisplayName("Every frontend's redirect and post-logout URI is registered on the shared client")
    void everyFrontendOnOneClient() throws Exception {
        when(encoder.encode(anyString())).thenReturn("{bcrypt}x");

        bootstrap(" https://center.example.cv/callback, https://app.example.cv/callback ,,",
                "https://center.example.cv/out,https://app.example.cv/out").seedDefaultOAuthClient().run();

        ArgumentCaptor<OAuthClientEntity> saved = ArgumentCaptor.forClass(OAuthClientEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRedirectUris())
                .isEqualTo(Set.of("https://center.example.cv/callback", "https://app.example.cv/callback"));
        assertThat(saved.getValue().getPostLogoutRedirectUris())
                .isEqualTo(Set.of("https://center.example.cv/out", "https://app.example.cv/out"));
        assertThat(saved.getValue().getAccessTokenTtl()).isEqualTo(300);
    }

    @Test
    @DisplayName("No URI configured: none registered; an existing client is left as it is")
    void blankAndExisting() throws Exception {
        when(encoder.encode(anyString())).thenReturn("{bcrypt}x");
        bootstrap("", "").seedDefaultOAuthClient().run();
        ArgumentCaptor<OAuthClientEntity> saved = ArgumentCaptor.forClass(OAuthClientEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRedirectUris()).isEmpty();
        assertThat(saved.getValue().getPostLogoutRedirectUris()).isEmpty();

        OAuthClientJpaRepository existing = mock(OAuthClientJpaRepository.class);
        when(existing.existsByClientId("igrp-access-management")).thenReturn(true);
        new DefaultOAuthClientBootstrap(existing, encoder, "igrp-access-management", "secret", 300, 86400, 300,
                "https://x.example.cv/callback", "").seedDefaultOAuthClient().run();
        verify(existing, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private DefaultOAuthClientBootstrap bootstrap(String redirectUris, String postLogoutUris) {
        return new DefaultOAuthClientBootstrap(repository, encoder, "igrp-access-management", "secret", 300, 86400,
                300, redirectUris, postLogoutUris);
    }
}
