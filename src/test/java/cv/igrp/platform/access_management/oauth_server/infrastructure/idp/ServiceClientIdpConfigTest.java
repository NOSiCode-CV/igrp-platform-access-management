package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import cv.igrp.framework.auth.autentika.adapter.AutentikaAdapter;
import cv.igrp.framework.auth.autentika.config.AutentikaSpringProperties;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.keycloak.adapter.KeycloakAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** v3-36 (#44): the IdP setting alone decides which adapter service clients go through. */
class ServiceClientIdpConfigTest {

    private final IAdapter keycloak = mock(KeycloakAdapter.class);
    private final IAdapter autentika = mock(AutentikaAdapter.class);

    @Test
    @DisplayName("The adapter matching the setting is accepted, whatever the case or spaces")
    void matchingAdapter() {
        assertDoesNotThrow(() -> ServiceClientIdpConfig.requireMatchingAdapter("keycloak", keycloak));
        assertDoesNotThrow(() -> ServiceClientIdpConfig.requireMatchingAdapter(" Autentika ", autentika));
    }

    @Test
    @DisplayName("An adapter that does not match the setting stops the startup, naming both")
    void mismatch() {
        IllegalStateException keycloakSetting = assertThrows(IllegalStateException.class,
                () -> ServiceClientIdpConfig.requireMatchingAdapter("keycloak", autentika));
        assertTrue(keycloakSetting.getMessage().contains("needs the KeycloakAdapter"), keycloakSetting.getMessage());

        IllegalStateException autentikaSetting = assertThrows(IllegalStateException.class,
                () -> ServiceClientIdpConfig.requireMatchingAdapter("autentika", keycloak));
        assertTrue(autentikaSetting.getMessage().contains("needs the AutentikaAdapter"), autentikaSetting.getMessage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "wso2", "permify"})
    @DisplayName("An unknown IdP stops the startup")
    void unknownIdp(String idp) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ServiceClientIdpConfig.requireMatchingAdapter(idp, keycloak));
        assertTrue(failure.getMessage().contains("must be 'keycloak' or 'autentika'"), failure.getMessage());
    }

    @Test
    @DisplayName("Autentika without its gateway settings stops the startup, naming every missing variable")
    void autentikaSettingsMissing() {
        var properties = new AutentikaSpringProperties(null, null, " ", null, Duration.ofSeconds(30));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ServiceClientIdpConfig.Autentika.configuration(properties));
        assertEquals("igrp.oauth.service-clients.idp=autentika needs IGRP_AUTENTIKA_REGISTRATION_ENDPOINT, "
                + "IGRP_AUTENTIKA_TOKEN_ENDPOINT, IGRP_AUTENTIKA_CONSUMER_KEY, IGRP_AUTENTIKA_CONSUMER_SECRET",
                failure.getMessage());
    }

    @Test
    @DisplayName("Autentika with its gateway settings gives its adapter")
    void autentikaSettings() {
        var properties = new AutentikaSpringProperties(URI.create("https://gateway.test/identity/register"),
                URI.create("https://gateway.test/token"), "key", "secret", Duration.ofSeconds(5));

        var configuration = ServiceClientIdpConfig.Autentika.configuration(properties);

        assertEquals(URI.create("https://gateway.test/identity/register"), configuration.registrationEndpoint());
        assertEquals(URI.create("https://gateway.test/token"), configuration.tokenEndpoint());
        assertEquals(Duration.ofSeconds(5), configuration.requestTimeout());
        assertTrue(new ServiceClientIdpConfig.Autentika().autentikaServiceClientAdapter(properties)
                instanceof AutentikaAdapter);
    }
}
