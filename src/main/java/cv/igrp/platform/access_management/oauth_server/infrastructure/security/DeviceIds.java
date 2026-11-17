package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The {@code device_id} claim of a user's access token (spec "Tokens"): the client's
 * {@value #HEADER} header when it sends one, otherwise a hash of the user agent, the address
 * and the client, so that requests from the same browser get the same id.
 */
final class DeviceIds {

    static final String HEADER = "X-Device-Id";
    private static final int MAX_LENGTH = 128;

    private DeviceIds() {
    }

    /** The device id of the current request, for {@code clientId}. */
    static String current(String clientId) {
        HttpServletRequest request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest() : null;
        return of(request, clientId);
    }

    static String of(HttpServletRequest request, String clientId) {
        if (request != null) {
            String supplied = request.getHeader(HEADER);
            if (supplied != null && !supplied.isBlank()) {
                return supplied.length() > MAX_LENGTH ? supplied.substring(0, MAX_LENGTH) : supplied;
            }
        }
        String seed = text(request == null ? null : request.getHeader("User-Agent")) + "|"
                + text(request == null ? null : request.getRemoteAddr()) + "|" + text(clientId);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            return "derived:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JRE.
            throw new IllegalStateException(e);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
