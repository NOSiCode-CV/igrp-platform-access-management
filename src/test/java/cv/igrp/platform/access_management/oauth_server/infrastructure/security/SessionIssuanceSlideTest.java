package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.session.config.SessionProperties;
import cv.igrp.platform.access_management.session.infrastructure.audit.SessionAuditLogger;
import cv.igrp.platform.access_management.session.infrastructure.cache.SessionCacheEvictService;
import cv.igrp.platform.access_management.session.infrastructure.metrics.SessionMetrics;
import cv.igrp.platform.access_management.session.infrastructure.persistence.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * The idle deadline set at token issuance must outlive the token being issued.
 * Otherwise a user working in a business app (whose backend never reports
 * activity to Access Management) idles out between two refreshes.
 */
class SessionIssuanceSlideTest {

    private SessionIssuanceService service(long idleSeconds) {
        SessionProperties props = new SessionProperties();
        ReflectionTestUtils.setField(props, "timeoutSeconds", idleSeconds);
        return new SessionIssuanceService(
                mock(SessionRepository.class),
                mock(SessionCacheEvictService.class),
                props,
                mock(SessionMetrics.class),
                mock(SessionAuditLogger.class));
    }

    private RegisteredClient client(Duration accessTokenTtl) {
        return RegisteredClient.withId("1")
                .clientId("app")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://app.example/callback")
                .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(accessTokenTtl).build())
                .build();
    }

    @Test
    void idleShorterThanToken_slidesPastTokenLifetime() {
        // The reported case: 30-min idle vs 60-min access token.
        // Must survive until the next refresh: 3600 + 300 grace.
        assertEquals(3900L, service(1800).effectiveSlideSeconds(client(Duration.ofMinutes(60))));
    }

    @Test
    void idleLongerThanToken_idleWins() {
        assertEquals(7200L, service(7200).effectiveSlideSeconds(client(Duration.ofMinutes(15))));
    }

    @Test
    void perClient_customLongTokenIsCovered() {
        // A team registering its own client with an 8h token must not idle out at 30 min.
        assertEquals(8 * 3600L + 300L, service(1800).effectiveSlideSeconds(client(Duration.ofHours(8))));
    }

    @Test
    void noClient_fallsBackToIdleTimeout() {
        assertEquals(1800L, service(1800).effectiveSlideSeconds(null));
    }
}
