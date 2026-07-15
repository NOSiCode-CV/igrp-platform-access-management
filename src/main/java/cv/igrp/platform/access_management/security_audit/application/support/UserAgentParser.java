package cv.igrp.platform.access_management.security_audit.application.support;

/**
 * Derives the {@code device} string of the Audit Report from a raw
 * {@code User-Agent} header at read time (no external dependency, R1.5.1).
 *
 * <p>Produces {@code "<Browser>/<OS>"} from a small lookup table
 * (Chrome/Firefox/Edge/Safari × Windows/macOS/Linux/Android/iOS). An
 * unrecognisable or blank UA yields {@code "Unknown"} — never {@code null}.
 */
public final class UserAgentParser {

    private static final String UNKNOWN = "Unknown";

    private UserAgentParser() {
    }

    public static String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String browser = detectBrowser(userAgent);
        String os = detectOs(userAgent);
        if (browser == null && os == null) {
            return UNKNOWN;
        }
        return (browser != null ? browser : UNKNOWN) + "/" + (os != null ? os : UNKNOWN);
    }

    private static String detectBrowser(String ua) {
        // Order matters: Edge and Chrome UAs both contain "Safari"; Edge contains "Chrome".
        if (ua.contains("Edg")) {
            return "Edge";
        }
        if (ua.contains("Chrome") || ua.contains("CriOS")) {
            return "Chrome";
        }
        if (ua.contains("Firefox") || ua.contains("FxiOS")) {
            return "Firefox";
        }
        if (ua.contains("Safari")) {
            return "Safari";
        }
        return null;
    }

    private static String detectOs(String ua) {
        // Order matters: Android UAs contain "Linux"; iOS UAs contain "like Mac OS X".
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux") || ua.contains("X11")) {
            return "Linux";
        }
        return null;
    }
}
