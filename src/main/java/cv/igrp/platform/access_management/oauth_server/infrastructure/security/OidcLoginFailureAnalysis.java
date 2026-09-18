package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies an OIDC login (oauth2Login callback) failure.
 *
 * <p>Spring reports every id_token validation problem under the generic
 * {@code invalid_id_token} error code, so a clock problem is indistinguishable
 * from an issuer or audience mismatch without reading the description. Two of
 * those descriptions are produced by {@code JwtTimestampValidator} and mean the
 * clocks disagree rather than the configuration being wrong:
 *
 * <ul>
 *   <li>{@code Jwt used before <instant>} — the token's {@code nbf} is in the
 *       FUTURE, i.e. this server's clock is behind the IdP's.</li>
 *   <li>{@code Jwt expired at <instant>} — the token's {@code exp} is in the
 *       PAST, i.e. this server's clock is ahead, or the token genuinely sat
 *       around too long before the callback.</li>
 * </ul>
 *
 * <p>Both are remapped to {@link #CLOCK_SKEW_CODE} and the measured difference
 * is captured, so the WARN line alone answers "how far off is the clock" and
 * support can tell a clock failure apart from a misconfiguration.
 */
record OidcLoginFailureAnalysis(String errorCode,
                                String rawDescription,
                                Duration skew,
                                Direction direction) {

    static final String CLOCK_SKEW_CODE = "id_token_clock_skew";
    static final String DEFAULT_CODE = "login_failed";

    /** Which way the clocks disagree, from this server's point of view. */
    enum Direction {
        /** Token not valid yet: this server's clock is behind the IdP's. */
        LOCAL_CLOCK_BEHIND,
        /** Token already expired: this server's clock is ahead, or the token is stale. */
        LOCAL_CLOCK_AHEAD_OR_TOKEN_STALE,
        /** Not a timestamp failure. */
        NONE
    }

    private static final Pattern USED_BEFORE = Pattern.compile("Jwt used before (\\S+)");
    private static final Pattern EXPIRED_AT = Pattern.compile("Jwt expired at (\\S+)");

    static OidcLoginFailureAnalysis of(AuthenticationException exception, Instant now) {
        String errorCode = DEFAULT_CODE;
        String description = null;

        if (exception instanceof OAuth2AuthenticationException oauthEx) {
            OAuth2Error error = oauthEx.getError();
            if (error != null) {
                if (error.getErrorCode() != null) {
                    errorCode = error.getErrorCode();
                }
                description = error.getDescription();
            }
        }

        if (description != null) {
            Matcher notYetValid = USED_BEFORE.matcher(description);
            if (notYetValid.find()) {
                Instant notBefore = parseInstant(notYetValid.group(1));
                if (notBefore != null) {
                    return new OidcLoginFailureAnalysis(CLOCK_SKEW_CODE, description,
                            Duration.between(now, notBefore), Direction.LOCAL_CLOCK_BEHIND);
                }
            }
            Matcher expired = EXPIRED_AT.matcher(description);
            if (expired.find()) {
                Instant expiresAt = parseInstant(expired.group(1));
                if (expiresAt != null) {
                    return new OidcLoginFailureAnalysis(CLOCK_SKEW_CODE, description,
                            Duration.between(expiresAt, now), Direction.LOCAL_CLOCK_AHEAD_OR_TOKEN_STALE);
                }
            }
        }

        return new OidcLoginFailureAnalysis(errorCode, description, null, Direction.NONE);
    }

    private static Instant parseInstant(String raw) {
        try {
            // Trim a trailing separator the validator may append after the instant.
            String cleaned = raw.endsWith(",") || raw.endsWith(".") ? raw.substring(0, raw.length() - 1) : raw;
            return Instant.parse(cleaned);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    boolean isClockSkew() {
        return CLOCK_SKEW_CODE.equals(errorCode);
    }

    /** Human-readable skew for the WARN line, e.g. {@code "1234s (LOCAL_CLOCK_BEHIND)"}. */
    String skewSummary() {
        if (skew == null) {
            return "(not a timestamp failure)";
        }
        return skew.toSeconds() + "s (" + direction + ")";
    }
}
