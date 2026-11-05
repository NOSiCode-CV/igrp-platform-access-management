package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

public class IgrpResponseStatusException extends ErrorResponseException {

    /**
     * Property key used in {@link ProblemDetail#getProperties()} to expose the
     * descriptive {@link IgrpErrorCode} returned by the API.
     */
    public static final String CODE_PROPERTY = "code";

    private static final Object[] NO_ARGS = new Object[0];

    private final IgrpErrorCode code;
    private final Object[] args;

    public IgrpResponseStatusException(HttpStatusCode status) {
        super(status);
        this.code = null;
        this.args = NO_ARGS;
    }

    public IgrpResponseStatusException(HttpStatusCode status, ProblemDetail body, Throwable cause) {
        super(status, body, cause);
        this.code = null;
        this.args = NO_ARGS;
    }

    private IgrpResponseStatusException(IgrpErrorCode code, Object[] args, ProblemDetail body, Throwable cause) {
        super(code.getStatus(), body, cause);
        this.code = code;
        this.args = args == null ? NO_ARGS : args.clone();
    }

    public IgrpErrorCode getCode() {
        return code;
    }

    /**
     * Message arguments supplied with the {@link IgrpErrorCode}. The final,
     * localized text is resolved from {@code error.<CODE>} with these arguments
     * by the {@code GlobalExceptionHandler}, because the request locale is only
     * known there.
     */
    public Object[] getArgs() {
        return args.clone();
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Preferred factory methods — backed by IgrpErrorCode
    // ───────────────────────────────────────────────────────────────────────

    /**
     * Creates an exception using a centrally-defined {@link IgrpErrorCode}. The
     * code's English message template is formatted with {@code args} and used as
     * the {@code ProblemDetail} title (this is also the text that shows up in
     * logs); the code itself is exposed in the {@code code} property of the
     * response body. The {@code GlobalExceptionHandler} replaces the title/detail
     * with the {@code error.<CODE>} message in the request locale.
     */
    public static IgrpResponseStatusException of(IgrpErrorCode code, Object... args) {
        return build(code, null, null, args);
    }

    /**
     * Same as {@link #of(IgrpErrorCode, Object...)} but additionally attaches a
     * {@code details} property to the {@code ProblemDetail}.
     */
    public static <T> IgrpResponseStatusException ofWithDetails(IgrpErrorCode code, T details, Object... args) {
        return build(code, details, null, args);
    }

    /**
     * Same as {@link #of(IgrpErrorCode, Object...)} but propagates the original cause.
     */
    public static IgrpResponseStatusException ofWithCause(IgrpErrorCode code, Throwable cause, Object... args) {
        return build(code, null, cause, args);
    }

    private static <T> IgrpResponseStatusException build(IgrpErrorCode code, T details, Throwable cause, Object... args) {
        var problemDetail = ProblemDetail.forStatus(code.getStatus());
        problemDetail.setTitle(code.formatMessage(args));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(CODE_PROPERTY, code.name());
        if (details != null) {
            properties.put("details", details);
        }
        problemDetail.setProperties(properties);

        return new IgrpResponseStatusException(code, args, problemDetail, cause);
    }

    // ───────────────────────────────────────────────────────────────────────
    //  Legacy factory methods — retained for callers not yet migrated
    //  to IgrpErrorCode. New code should use the IgrpErrorCode variants above.
    // ───────────────────────────────────────────────────────────────────────

    public static IgrpResponseStatusException of(HttpStatus status) {
        var problemDetail = ProblemDetail.forStatus(status);
        return new IgrpResponseStatusException(status, problemDetail, null);
    }

    public static IgrpResponseStatusException of(HttpStatus status, String title) {
        var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        return new IgrpResponseStatusException(status, problemDetail, null);
    }

    public static <T> IgrpResponseStatusException of(HttpStatus status, String title, T details) {
        var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        if (details != null) {
            problemDetail.setProperties(Map.of("details", details));
        }
        return new IgrpResponseStatusException(status, problemDetail, null);
    }

    public static <T> IgrpResponseStatusException notFound(String title, T details) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle(title);
        if (details != null) {
            problemDetail.setProperties(Map.of("details", details));
        }
        return new IgrpResponseStatusException(HttpStatus.NOT_FOUND, problemDetail, null);
    }

    public static IgrpResponseStatusException badRequest(String title) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(title);
        return new IgrpResponseStatusException(HttpStatus.BAD_REQUEST, problemDetail, null);
    }

    public static IgrpResponseStatusException forbidden(String title) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle(title);
        return new IgrpResponseStatusException(HttpStatus.FORBIDDEN, problemDetail, null);
    }

    public static <T> IgrpResponseStatusException badRequest(String title, T details) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(title);
        if (details != null) {
            problemDetail.setProperties(Map.of("details", details));
        }
        return new IgrpResponseStatusException(HttpStatus.BAD_REQUEST, problemDetail, null);
    }

    public static IgrpResponseStatusException notFound(String title) {
        return notFound(title, null);
    }

    public static IgrpResponseStatusException internalServerError(String title, String details) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(title);
        if (details != null) {
            problemDetail.setProperties(Map.of("details", details));
        }
        return new IgrpResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, problemDetail, null);
    }

    public static IgrpResponseStatusException internalServerError(String title) {
        var problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle(title);
        return new IgrpResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, problemDetail, null);
    }

}
