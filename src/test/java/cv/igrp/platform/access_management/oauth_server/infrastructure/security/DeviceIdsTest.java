package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/** The {@code device_id} claim (spec "Tokens", #33). */
class DeviceIdsTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void theClientsHeaderWins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIds.HEADER, "device-1");

        assertThat(DeviceIds.of(request, "client-a")).isEqualTo("device-1");
    }

    @Test
    void aLongHeaderIsCut() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIds.HEADER, "x".repeat(300));

        assertThat(DeviceIds.of(request, "client-a")).hasSize(128);
    }

    @Test
    void withoutTheHeaderTheSameBrowserGetsTheSameId() {
        MockHttpServletRequest first = browser("10.0.0.1");
        MockHttpServletRequest again = browser("10.0.0.1");
        MockHttpServletRequest other = browser("10.0.0.2");

        assertThat(DeviceIds.of(first, "client-a")).startsWith("derived:").hasSize("derived:".length() + 32)
                .isEqualTo(DeviceIds.of(again, "client-a"))
                .isNotEqualTo(DeviceIds.of(other, "client-a"))
                .isNotEqualTo(DeviceIds.of(first, "client-b"));
    }

    @Test
    void outsideARequestStillAnId() {
        assertThat(DeviceIds.current("client-a")).startsWith("derived:").isEqualTo(DeviceIds.of(null, "client-a"));
    }

    @Test
    void theCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceIds.HEADER, "device-2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(DeviceIds.current("client-a")).isEqualTo("device-2");
    }

    private static MockHttpServletRequest browser(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Firefox");
        request.setRemoteAddr(address);
        return request;
    }
}
