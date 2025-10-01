package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.util.Map;

public class IgrpResponseStatusException extends ErrorResponseException {

    public IgrpResponseStatusException(HttpStatusCode status) {
        super(status);
    }

    public IgrpResponseStatusException(HttpStatusCode status, ProblemDetail body, Throwable cause) {
        super(status, body, cause);
    }

    public static IgrpResponseStatusException of(HttpStatus status) {
        return new IgrpResponseStatusException(status);
    }

    public static IgrpResponseStatusException of(HttpStatus status, String title) {
        var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        return new IgrpResponseStatusException(status, problemDetail, null);
    }

    public static <T> IgrpResponseStatusException of(HttpStatus status, String title, T details) {
        var problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle(title);
        problemDetail.setProperties(Map.of("details", details));
        return new IgrpResponseStatusException(status, problemDetail, null);
    }

    public static <T> IgrpResponseStatusException notFound(String title, T details) {
        return of(HttpStatus.NOT_FOUND, title, details);
    }

    public static IgrpResponseStatusException badRequest(String title) {
        return of(HttpStatus.BAD_REQUEST, title);
    }

    public static IgrpResponseStatusException forbidden(String title) {
        return of(HttpStatus.FORBIDDEN, title);
    }

    public static <T> IgrpResponseStatusException badRequest(String title, T details) {
        return of(HttpStatus.BAD_REQUEST, title, details);
    }

    public static IgrpResponseStatusException notFound(String title) {
        return notFound(title, null);
    }

    public static IgrpResponseStatusException internalServerError(String title, String details) {
        return of(HttpStatus.INTERNAL_SERVER_ERROR, title, details);

    }

    public static IgrpResponseStatusException internalServerError(String title) {
        return of(HttpStatus.INTERNAL_SERVER_ERROR, title);
    }

}