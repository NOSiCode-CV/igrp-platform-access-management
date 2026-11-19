package cv.igrp.platform.access_management.oauth_server.infrastructure.idp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cv.igrp.framework.auth.autentika.adapter.AutentikaAdapter;
import cv.igrp.framework.auth.autentika.config.AutentikaAutoConfiguration;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.keycloak.adapter.KeycloakAdapter;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v3-36 (#44): AM's service-client wiring against both IdP libraries' real
 * auto-configurations, and, for Autentika, the real adapter over HTTP against a stub of
 * the gateway's token and registration contract.
 */
class ServiceClientIdpIntegrationTest {

    private static final String REGISTER = "/identity/register/1.0.0/register";
    private static final String TOKEN = "/token";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AutentikaAutoConfiguration.class,
                    cv.igrp.framework.auth.keycloak.config.AutoConfiguration.class))
            .withUserConfiguration(ServiceClientIdpConfig.class, FederatedClients.class);

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer gateway;

    @BeforeEach
    void startGateway() throws IOException {
        gateway = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        gateway.createContext(TOKEN, exchange -> reply(exchange, 200,
                "{\"access_token\":\"gateway-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        gateway.createContext(REGISTER, exchange -> reply(exchange, 201,
                "{\"client_id\":\"svc-billing\",\"client_secret\":\"issued-secret\",\"client_secret_expires_at\":0,"
                        + "\"client_name\":\"Billing\",\"grant_types\":[\"client_credentials\"],\"redirect_uris\":[]}"));
        gateway.start();
    }

    @AfterEach
    void stopGateway() {
        gateway.stop(0);
    }

    private void reply(HttpExchange exchange, int status, String body) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " "
                + exchange.getRequestHeaders().getFirst("Authorization"));
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String gatewayUrl(String path) {
        return "http://127.0.0.1:" + gateway.getAddress().getPort() + path;
    }

    private String[] autentikaGateway() {
        return new String[]{
                "igrp.autentika.registration-endpoint=" + gatewayUrl(REGISTER),
                "igrp.autentika.token-endpoint=" + gatewayUrl(TOKEN),
                "igrp.autentika.consumer-key=consumer",
                "igrp.autentika.consumer-secret=consumer-secret"};
    }

    @Test
    @DisplayName("Default: Keycloak's adapter is the only one, and clients are stored as Keycloak's")
    void keycloakByDefault() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertInstanceOf(KeycloakAdapter.class, context.getBean(IAdapter.class));
            assertEquals("keycloak", context.getBean(FederatedClients.class).idp());
        });
    }

    @Test
    @DisplayName("idp=autentika: Autentika's adapter is the only one; a client is registered at the gateway "
            + "and its secret returned once")
    void autentikaCreatesClients() {
        runner.withPropertyValues("igrp.oauth.service-clients.idp=autentika")
                .withPropertyValues(autentikaGateway())
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(1, context.getBeansOfType(IAdapter.class).size());
                    assertInstanceOf(AutentikaAdapter.class, context.getBean(IAdapter.class));
                    FederatedClients clients = context.getBean(FederatedClients.class);
                    assertEquals("autentika", clients.idp());

                    var issued = clients.create("svc-billing", "Billing");

                    assertEquals("svc-billing", issued.clientId());
                    assertEquals("issued-secret", issued.clientSecret());
                    assertTrue(requests.contains("POST " + REGISTER + " Bearer gateway-token"), requests.toString());
                });
    }

    @Test
    @DisplayName("idp=autentika: rotating a secret answers 501 IGRP_AUTH_IDP_OPERATION_UNSUPPORTED and calls "
            + "no gateway")
    void autentikaCannotRotate() {
        runner.withPropertyValues("igrp.oauth.service-clients.idp=autentika")
                .withPropertyValues(autentikaGateway())
                .run(context -> {
                    FederatedClients clients = context.getBean(FederatedClients.class);
                    IgrpResponseStatusException failure = assertThrows(IgrpResponseStatusException.class,
                            () -> clients.rotateSecret("svc-billing"));

                    assertEquals(501, failure.getStatusCode().value());
                    assertEquals(IgrpErrorCode.IGRP_AUTH_IDP_OPERATION_UNSUPPORTED, failure.getCode());
                    assertTrue(requests.isEmpty(), requests.toString());
                });
    }

    @Test
    @DisplayName("idp=autentika without the gateway settings: AM does not start")
    void autentikaNeedsItsSettings() {
        runner.withPropertyValues("igrp.oauth.service-clients.idp=autentika").run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            assertTrue(rootMessage(failure).contains("IGRP_AUTENTIKA_REGISTRATION_ENDPOINT"), rootMessage(failure));
        });
    }

    @Test
    @DisplayName("idp=keycloak with igrp.autentika.enabled=true: AM does not start rather than use the wrong IdP")
    void mismatchRefused() {
        runner.withPropertyValues("igrp.oauth.service-clients.idp=keycloak", "igrp.autentika.enabled=true")
                .withPropertyValues(autentikaGateway())
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertNotNull(failure);
                    assertTrue(rootMessage(failure).contains("needs the KeycloakAdapter"), rootMessage(failure));
                });
    }

    @Test
    @DisplayName("An unknown idp: AM does not start")
    void unknownRefused() {
        runner.withPropertyValues("igrp.oauth.service-clients.idp=wso2").run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            assertTrue(rootMessage(failure).contains("must be 'keycloak' or 'autentika'"), rootMessage(failure));
        });
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
