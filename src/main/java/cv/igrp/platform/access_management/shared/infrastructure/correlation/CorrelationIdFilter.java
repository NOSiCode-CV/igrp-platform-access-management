package cv.igrp.platform.access_management.shared.infrastructure.correlation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Binds one correlation id to each request (v3-24): in the log MDC under
 * {@link CorrelationIds#MDC_KEY}, as the {@link #REQUEST_ATTRIBUTE} request
 * attribute read by the security audit, and in the {@code X-Correlation-ID}
 * response header.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ATTRIBUTE = CorrelationIds.MDC_KEY;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
        request.setAttribute(REQUEST_ATTRIBUTE, id);
        response.setHeader(CorrelationIds.HEADER, id);
        MDC.put(CorrelationIds.MDC_KEY, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }
}
