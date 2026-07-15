package cv.igrp.platform.access_management.security_audit.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentParserTest {

    @Test
    void parsesChromeOnWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        assertThat(UserAgentParser.parse(ua)).isEqualTo("Chrome/Windows");
    }

    @Test
    void parsesFirefoxOnLinux() {
        String ua = "Mozilla/5.0 (X11; Linux x86_64; rv:121.0) Gecko/20100101 Firefox/121.0";
        assertThat(UserAgentParser.parse(ua)).isEqualTo("Firefox/Linux");
    }

    @Test
    void parsesSafariOnMac() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.0 Safari/605.1.15";
        assertThat(UserAgentParser.parse(ua)).isEqualTo("Safari/macOS");
    }

    @Test
    void parsesEdgeOnWindows() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0";
        assertThat(UserAgentParser.parse(ua)).isEqualTo("Edge/Windows");
    }

    @Test
    void parsesChromeOnAndroidNotLinux() {
        String ua = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
        assertThat(UserAgentParser.parse(ua)).isEqualTo("Chrome/Android");
    }

    @Test
    void unknownUserAgentReturnsUnknownNeverNull() {
        assertThat(UserAgentParser.parse("totally-not-a-browser")).isEqualTo("Unknown");
    }

    @Test
    void nullOrBlankReturnsUnknown() {
        assertThat(UserAgentParser.parse(null)).isEqualTo("Unknown");
        assertThat(UserAgentParser.parse("   ")).isEqualTo("Unknown");
    }
}
