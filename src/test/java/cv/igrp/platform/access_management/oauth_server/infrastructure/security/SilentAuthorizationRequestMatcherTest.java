package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SilentAuthorizationRequestMatcherTest {

    @ParameterizedTest(name = "{0} prompt={1} -> {2}")
    @DisplayName("Matches only the authorization endpoint with prompt=none (#46)")
    @CsvSource(nullValues = "NULL", value = {
            "/oauth2/authorize, none, true",
            "/oauth2/authorize, ' none ', true",
            "/oauth2/authorize, none consent, true",
            "/igrp-access-management/oauth2/authorize, none, true",
            "/oauth2/authorize, login, false",
            "/oauth2/authorize, nonexistent, false",
            "/oauth2/authorize, '', false",
            "/oauth2/authorize, NULL, false",
            "/oauth2/token, none, false",
            "/oauth2/authorize/extra, none, false",
    })
    void matches(String uri, String prompt, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (prompt != null) {
            request.setParameter("prompt", prompt);
        }

        assertEquals(expected, new SilentAuthorizationRequestMatcher().matches(request));
    }

    @Test
    @DisplayName("A request without a URI does not match")
    void noUri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(null);
        request.setParameter("prompt", "none");

        assertFalse(new SilentAuthorizationRequestMatcher().matches(request));
    }
}
