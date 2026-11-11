package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import cv.igrp.platform.access_management.shared.infrastructure.correlation.CorrelationIdFilter;
import cv.igrp.platform.access_management.shared.infrastructure.correlation.CorrelationIds;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-24 (#31): the failure id a user reports from the OIDC login error page is
 * the request's correlation id, so it also finds the request's audit rows and
 * its trace, not only the log line.
 */
class OidcLoginFailureCorrelationTest {

    private static final String FAILURE_ID = "X-IGRP-Login-Failure-Id";

    private final AuthorizationServerConfig.OidcLoginFailureHandler handler =
            new AuthorizationServerConfig.OidcLoginFailureHandler();

    @Test
    void theFailureIdIsTheRequestsCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/external-idp");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "4bf92f3577b34da6a3ce929d0e0e4736");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, failure());

        assertThat(response.getHeader(FAILURE_ID)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(response.getContentAsString()).contains("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void withoutTheFilterTheIdFollowsTheSameRules() throws Exception {
        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.addHeader(CorrelationIds.HEADER, "order-42");
        MockHttpServletResponse validResponse = new MockHttpServletResponse();
        MockHttpServletResponse noneResponse = new MockHttpServletResponse();

        handler.onAuthenticationFailure(valid, validResponse, failure());
        handler.onAuthenticationFailure(new MockHttpServletRequest(), noneResponse, failure());

        assertThat(validResponse.getHeader(FAILURE_ID)).isEqualTo("order-42");
        assertThat(noneResponse.getHeader(FAILURE_ID)).matches("[0-9a-f]{32}");
    }

    private static OAuth2AuthenticationException failure() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token", "signature check failed", null));
    }
}
