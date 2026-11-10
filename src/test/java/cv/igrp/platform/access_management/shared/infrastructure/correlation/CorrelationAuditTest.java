package cv.igrp.platform.access_management.shared.infrastructure.correlation;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditContextProvider;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RevisionInfoEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.revision.RevisionInfoListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-24 (#31): in AM, the correlation id an audit row and an Envers revision
 * carry is the request's trace id when tracing is on, else a valid
 * {@code X-Correlation-ID}, else a random id. The compat suite runs with the
 * OTel SDK off, so the tracing-on half is covered here with the OTel API.
 */
class CorrelationAuditTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final SecurityAuditContextProvider provider = new SecurityAuditContextProvider();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void withTracingOnTheAuditRowAndTheRevisionCarryTheTraceId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/departments");
        request.addHeader(CorrelationIds.HEADER, "ignored-when-tracing");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Map<String, Object>> auditContext = new AtomicReference<>();
        AtomicReference<RevisionInfoEntity> revision = new AtomicReference<>();

        try (Scope ignored = activeSpan()) {
            new CorrelationIdFilter().doFilter(request, response, new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                    auditContext.set(provider.getContext());
                    revision.set(newRevision());
                }
            });
        }

        assertThat(auditContext.get()).containsEntry("correlationId", TRACE_ID);
        assertThat(revision.get().getTraceId()).isEqualTo(TRACE_ID);
        assertThat(response.getHeader(CorrelationIds.HEADER)).isEqualTo(TRACE_ID);
        assertThat(MDC.get(CorrelationIds.MDC_KEY)).as("cleared after the request").isNull();
    }

    @Test
    void aRequestThatSkippedTheFilterStillGetsAValidatedId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "line\r\nbreak");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getContext().get("correlationId")).asString().matches("[0-9a-f]{32}");
    }

    @Test
    void outsideARequestTheAuditRowCarriesTheTraceIdElseARandomId() {
        assertThat(provider.getContext().get("correlationId")).asString().matches("[0-9a-f]{32}");
        assertThat(newRevision().getTraceId()).matches("[0-9a-f]{32}");

        try (Scope ignored = activeSpan()) {
            assertThat(provider.getContext()).containsEntry("correlationId", TRACE_ID);
            assertThat(newRevision().getTraceId()).isEqualTo(TRACE_ID);
        }
    }

    @Test
    void aRequestThatSkippedTheFilterKeepsOneIdForAllItsRows() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        Object first = provider.getContext().get("correlationId");

        assertThat(provider.getContext()).containsEntry("correlationId", first);
    }

    @Test
    void theRevisionNamesTheUserLikeCreatedBy() {
        assertThat(newRevision().getUsername()).isEqualTo("system");

        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("sub-1")
                .claim("preferred_username", "alice").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES));

        assertThat(newRevision().getUsername()).isEqualTo("alice");
    }

    @Test
    void headerRules() {
        assertThat(CorrelationIds.resolve("order-42_retry")).isEqualTo("order-42_retry");
        assertThat(CorrelationIds.resolve("x".repeat(64))).isEqualTo("x".repeat(64));
        assertThat(CorrelationIds.resolve("x".repeat(65))).matches("[0-9a-f]{32}");
        assertThat(CorrelationIds.resolve(null)).matches("[0-9a-f]{32}");
        assertThat(CorrelationIds.resolve("has space")).matches("[0-9a-f]{32}");
    }

    private static RevisionInfoEntity newRevision() {
        RevisionInfoEntity revision = new RevisionInfoEntity();
        new RevisionInfoListener().newRevision(revision);
        return revision;
    }

    private static Scope activeSpan() {
        return Span.wrap(SpanContext.create(TRACE_ID, "00f067aa0ba902b7", TraceFlags.getSampled(),
                TraceState.getDefault())).makeCurrent();
    }
}
