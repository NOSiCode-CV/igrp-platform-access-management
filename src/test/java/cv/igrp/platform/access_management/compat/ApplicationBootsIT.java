package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationBootsIT extends AbstractCompatIT {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("The application starts and serves its own OIDC discovery document")
    void discoveryDocument() throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(CompatEnvironment.ISSUER + "/.well-known/openid-configuration")).build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"issuer\":\"" + CompatEnvironment.ISSUER + "\""), response.body());
    }
}
