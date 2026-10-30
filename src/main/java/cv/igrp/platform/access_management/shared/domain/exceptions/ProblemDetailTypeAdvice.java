package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.net.URI;

/**
 * Keeps {@code "type":"about:blank"} in every error body.
 *
 * <p>Spring Framework 7 leaves {@link ProblemDetail#getType()} null by default,
 * so the member disappeared from the JSON. RFC 9457 treats a missing type as
 * {@code about:blank}, but clients have always received it explicitly, and the
 * API contract does not change in this migration.
 */
@ControllerAdvice
public class ProblemDetailTypeAdvice implements ResponseBodyAdvice<Object> {

    private static final URI BLANK_TYPE = URI.create("about:blank");

    @Override
    public boolean supports(@NotNull MethodParameter returnType,
                            @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType,
                                  @NotNull MediaType selectedContentType,
                                  @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem && problem.getType() == null) {
            problem.setType(BLANK_TYPE);
        }
        return body;
    }
}
