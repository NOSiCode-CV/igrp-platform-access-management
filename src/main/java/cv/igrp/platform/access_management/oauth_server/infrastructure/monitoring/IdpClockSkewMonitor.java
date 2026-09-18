package cv.igrp.platform.access_management.oauth_server.infrastructure.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically compares this server's clock against the upstream IdP's.
 *
 * <p>Clock drift against the IdP first shows up as an unexplained login outage:
 * the id_token's {@code nbf} lands in the future relative to this server and
 * Spring rejects it with {@code invalid_id_token}. By then users are already
 * locked out. This turns that into an alert that fires beforehand.
 *
 * <p>The IdP's clock is read from the {@code Date} response header of its OIDC
 * discovery document — every HTTP server sends one, so this needs no special
 * endpoint and no credentials. Resolution is one second (RFC 7231), so
 * differences below {@link #MIN_SIGNIFICANT_SKEW_SECONDS} are treated as noise.
 *
 * <p><b>Why this never reports DOWN.</b> A readiness probe wired to health
 * would take the whole service out of rotation over a clock difference that
 * does not stop most traffic from working. The gauge
 * {@code igrp.oauth.idp.clock.skew.seconds} and the WARN log carry the alert;
 * health only exposes the measurement as detail.
 *
 * <p>Note the blast radius is wider than login: a wrong clock also corrupts the
 * {@code iat}/{@code exp} of tokens this server issues, session idle/absolute
 * timeouts, OTP expiry and every audit timestamp.
 */
@Component
@ConditionalOnProperty(name = "igrp.oauth.external-idp.clock-check-enabled",
        havingValue = "true", matchIfMissing = true)
public class IdpClockSkewMonitor implements HealthIndicator {

    private static final Logger LOG = LoggerFactory.getLogger(IdpClockSkewMonitor.class);

    /** HTTP Date has one-second resolution; anything under this is measurement noise. */
    static final long MIN_SIGNIFICANT_SKEW_SECONDS = 2L;

    private static final String DISCOVERY_SUFFIX = "/.well-known/openid-configuration";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final String registrationId;
    private final long warnThresholdSeconds;

    private final AtomicLong lastSkewSeconds = new AtomicLong(0L);
    private final AtomicReference<Instant> lastCheckAt = new AtomicReference<>();
    private final AtomicReference<String> lastError = new AtomicReference<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public IdpClockSkewMonitor(
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            MeterRegistry meterRegistry,
            @Value("${igrp.oauth.external-idp.registration-id:external-idp}") String registrationId,
            @Value("${igrp.oauth.external-idp.clock-skew-warn-seconds:30}") long warnThresholdSeconds) {
        this.clientRegistrations = clientRegistrations;
        this.registrationId = registrationId;
        this.warnThresholdSeconds = warnThresholdSeconds;

        Gauge.builder("igrp.oauth.idp.clock.skew.seconds", lastSkewSeconds, AtomicLong::doubleValue)
                .description("Difference between this server's clock and the upstream IdP's, "
                        + "in seconds. Positive means this server is ahead.")
                .register(meterRegistry);
    }

    /** First measurement as soon as the app is serving, so drift is caught before a user hits it. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        check();
    }

    @Scheduled(initialDelay = 60_000L,
            fixedDelayString = "${igrp.oauth.external-idp.clock-check-interval-seconds:900}000")
    public void checkPeriodically() {
        check();
    }

    /** Best-effort: a monitoring probe must never disturb the running application. */
    void check() {
        try {
            String issuerUri = resolveIssuerUri();
            if (issuerUri == null || issuerUri.isBlank()) {
                return; // federation not configured — nothing to compare against
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(issuerUri) + DISCOVERY_SUFFIX))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            Optional<Instant> idpTime = response.headers().firstValue("date").flatMap(IdpClockSkewMonitor::parseHttpDate);
            if (idpTime.isEmpty()) {
                lastError.set("IdP response carried no parsable Date header");
                return;
            }

            long skew = skewSeconds(idpTime.get(), Instant.now());
            lastSkewSeconds.set(skew);
            lastCheckAt.set(Instant.now());
            lastError.set(null);

            if (exceedsThreshold(skew, warnThresholdSeconds)) {
                LOG.warn("[IDP CLOCK SKEW] This server's clock differs from the identity provider by {}s "
                                + "(threshold {}s, {}). Logins will start failing with id_token_clock_skew once the "
                                + "difference exceeds the id_token tolerance. Check NTP on this host. "
                                + "Token expiry, session timeouts, OTP validity and audit timestamps are affected too.",
                        skew, warnThresholdSeconds, skew > 0 ? "this server is AHEAD" : "this server is BEHIND");
            } else {
                LOG.debug("[IDP CLOCK SKEW] within tolerance: {}s (threshold {}s)", skew, warnThresholdSeconds);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            lastError.set("interrupted");
        } catch (Exception ex) {
            // Unreachable IdP, DNS failure, TLS problem — not this monitor's business to escalate.
            lastError.set(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            LOG.debug("[IDP CLOCK SKEW] check failed: {}", ex.toString());
        }
    }

    private String resolveIssuerUri() {
        ClientRegistrationRepository repository = clientRegistrations.getIfAvailable();
        if (repository == null) {
            return null;
        }
        ClientRegistration registration = repository.findByRegistrationId(registrationId);
        if (registration == null || registration.getProviderDetails() == null) {
            return null;
        }
        return registration.getProviderDetails().getIssuerUri();
    }

    // ─── pure helpers (unit-tested) ──────────────────────────────────────────

    static Optional<Instant> parseHttpDate(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ZonedDateTime.parse(headerValue.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** Positive when this server is AHEAD of the IdP. */
    static long skewSeconds(Instant idpTime, Instant localTime) {
        return Duration.between(idpTime, localTime).toSeconds();
    }

    static boolean exceedsThreshold(long skew, long thresholdSeconds) {
        long magnitude = Math.abs(skew);
        return magnitude >= MIN_SIGNIFICANT_SKEW_SECONDS && magnitude > thresholdSeconds;
    }

    private static String trimTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    @Override
    public Health health() {
        long skew = lastSkewSeconds.get();
        Instant checkedAt = lastCheckAt.get();
        Health.Builder builder = Health.up()
                .withDetail("skewSeconds", skew)
                .withDetail("warnThresholdSeconds", warnThresholdSeconds)
                .withDetail("withinThreshold", !exceedsThreshold(skew, warnThresholdSeconds))
                .withDetail("lastCheckAt", checkedAt != null ? checkedAt.toString() : "never");
        String error = lastError.get();
        if (error != null) {
            builder.withDetail("lastError", error);
        }
        return builder.build();
    }
}
