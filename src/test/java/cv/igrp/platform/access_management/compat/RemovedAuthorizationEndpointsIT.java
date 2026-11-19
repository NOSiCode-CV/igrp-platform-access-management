package cv.igrp.platform.access_management.compat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * v3-30 (#38; spec "Removed from AM 0.3"): the per-check authorization endpoints are gone.
 * Nothing on the 0.3 line calls them: services decide from the permission set, and
 * frontends ask {@code POST /api/authorize/page}. A signed-in user, even root, gets 404.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RemovedAuthorizationEndpointsIT extends AbstractCompatIT {

    @Autowired
    ApplicationContext context;

    CompatSupport api;

    @BeforeAll
    void setUp() {
        api = new CompatSupport(context);
    }

    @ParameterizedTest(name = "POST {0} answers 404")
    @ValueSource(strings = {"/api/authorize/check", "/api/authorize/batch-check"})
    @DisplayName("The removed check endpoints answer 404")
    void removed(String path) {
        HttpResponse<String> response = api.postJson(path, api.superadminToken(),
                "{\"resource\":\"igrp-access-management\",\"action\":\"igrp.users.list\"}");

        assertEquals(404, response.statusCode(), response.body());
    }
}
