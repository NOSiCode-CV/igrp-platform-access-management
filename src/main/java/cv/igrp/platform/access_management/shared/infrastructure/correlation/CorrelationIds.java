package cv.igrp.platform.access_management.shared.infrastructure.correlation;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The id that joins an audit row, an Envers revision, the logs and the trace in
 * Tempo (v3-24). It is the OTel trace id; without tracing, a valid
 * {@code X-Correlation-ID} header; otherwise a random id.
 *
 * <p>The header is caller-controlled and ends up in audit rows and logs, so it
 * is only accepted when it is 1 to 64 letters, digits, {@code -} or {@code _}.
 *
 * <p>Same rules as the 0.3 starter's {@code cv.igrp.framework.auth.core.audit.CorrelationIds}.
 * AM keeps its own copy until it adopts the starter (decision on #8).
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private CorrelationIds() {
    }

    /** The correlation id for a request that sent {@code headerValue} (may be null). */
    public static String resolve(String headerValue) {
        return traceId().orElseGet(() -> isValid(headerValue) ? headerValue : random());
    }

    /** The id bound to the current request, else the current trace id; empty outside both. */
    public static Optional<String> current() {
        String bound = MDC.get(MDC_KEY);
        return bound != null ? Optional.of(bound) : traceId();
    }

    static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    private static Optional<String> traceId() {
        SpanContext span = Span.current().getSpanContext();
        return span.isValid() ? Optional.of(span.getTraceId()) : Optional.empty();
    }

    private static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
