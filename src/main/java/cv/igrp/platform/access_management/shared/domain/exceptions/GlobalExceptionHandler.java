package cv.igrp.platform.access_management.shared.domain.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import cv.igrp.platform.access_management.department.domain.exceptions.OutOfScopeException;
import cv.igrp.platform.access_management.department.domain.exceptions.RootDepartmentForbiddenException;
import cv.igrp.platform.access_management.shared.i18n.Messages;
import cv.igrp.platform.access_management.shared.security.InvalidPrincipalException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps exceptions to RFC 7807 {@link ProblemDetail} responses.
 *
 * <p><b>i18n:</b> every user-facing text is resolved through {@link Messages} in the
 * request locale (Accept-Language → platform default): {@link IgrpErrorCode}-backed
 * exceptions use {@code error.<CODE>}, handler titles use {@code error.handler.*}.
 * Log lines keep the English text carried by the exception itself.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Messages messages;

    public GlobalExceptionHandler(Messages messages) {
        this.messages = messages;
    }

    @ExceptionHandler(IgrpResponseStatusException.class)
    public ProblemDetail handleIgrpResponseStatusException(IgrpResponseStatusException ex) {

        // Logged before localization: the exception body carries the English
        // enum template, which is what operators search for in the logs.
        LOGGER.error(ex.getMessage(), ex);

        return localize(ex);
    }

    /**
     * Returns a copy of the exception body whose {@code title} and {@code detail}
     * carry the {@code error.<CODE>} message in the request locale. The {@code code}
     * property and every other property are kept as-is (NFR-5). Exceptions built
     * with the legacy free-text factories (no {@link IgrpErrorCode}) are returned
     * unchanged. The exception's own body is never mutated.
     */
    private ProblemDetail localize(IgrpResponseStatusException ex) {
        ProblemDetail body = ex.getBody();
        IgrpErrorCode code = ex.getCode();
        if (code == null) {
            return body;
        }
        Object[] args = ex.getArgs();
        String text = messages.getOrDefault(code.messageKey(), code.formatMessage(args), args);

        ProblemDetail localized = ProblemDetail.forStatus(body.getStatus());
        localized.setType(body.getType());
        localized.setInstance(body.getInstance());
        localized.setTitle(text);
        localized.setDetail(text);
        if (body.getProperties() != null) {
            localized.setProperties(new LinkedHashMap<>(body.getProperties()));
        }
        return localized;
    }

    @ExceptionHandler(OutOfScopeException.class)
    public ProblemDetail handleOutOfScope(OutOfScopeException ex) {
        LOGGER.warn("Out-of-scope department access attempt: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle(messages.get("error.handler.outOfScope"));
        problem.setProperty("error", "OUT_OF_SCOPE");
        problem.setProperty("departmentId", ex.getDepartmentId());
        return problem;
    }

    @ExceptionHandler(RootDepartmentForbiddenException.class)
    public ProblemDetail handleRootDepartmentForbidden(RootDepartmentForbiddenException ex) {
        LOGGER.warn("Root-department creation attempted by non-superadmin");
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle(messages.get("error.handler.rootDepartmentForbidden"));
        problem.setProperty("error", "ROOT_DEPARTMENT_FORBIDDEN");
        return problem;
    }

    @ExceptionHandler(ClassCastException.class)
    public ProblemDetail handleClassCastException(ClassCastException ex) {

        var stackTrace = ex.getStackTrace();

        var origin = stackTrace.length > 0 ? stackTrace[0] : null;

        var originMsg = Optional.ofNullable(origin)
                .map(or ->
                        " at %s.%s(%s:%d)".formatted(
                                or.getClassName(),
                                or.getMethodName(),
                                or.getFileName(),
                                or.getLineNumber()
                        ))
                .orElse("");

        var detailedMessage = ex.getMessage() + originMsg;

        LOGGER.error("CLASS CAST EXCEPTION: {}", detailedMessage, ex);

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {

        var errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : messages.get("error.handler.invalidValue"),
                        // Several constraints can fail on the same field: keep the first.
                        (first, second) -> first,
                        LinkedHashMap::new)
                );

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(messages.get("error.handler.validationErrors"));
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {

        var errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage,
                        (first, second) -> first, LinkedHashMap::new));

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle(messages.get("error.handler.constraintViolationErrors"));
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {

        LOGGER.error("HTTP MESSAGE NOT READABLE EXCEPTION", ex);

        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {

            var targetType = ife.getTargetType();

            var allowedValues = Arrays.stream(targetType.getEnumConstants())
                    .map(Object::toString)
                    .toArray(String[]::new);

            problem.setTitle(messages.get("error.handler.invalidEnumValue", targetType.getSimpleName()));
            problem.setProperty("CurrentValue", ife.getValue());
            problem.setProperty("AllowedValues", allowedValues);
            return problem;
        }

        problem.setTitle(messages.get("error.handler.malformedJson"));
        problem.setDetail(ex.getMessage());

        return problem;
    }

    /**
     * Phase G1 / FR-13 — map {@link InvalidPrincipalException} (thrown by
     * {@code SubjectParser.parseUserSubjectOrThrow} when a JWT {@code sub}
     * cannot be turned into a numeric user id) to a clean HTTP 401 with a
     * standards-shaped {@code WWW-Authenticate} challenge. Replaces the
     * historical 500 {@link NumberFormatException} from the permission cache.
     */
    @ExceptionHandler(InvalidPrincipalException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPrincipal(InvalidPrincipalException ex) {
        LOGGER.warn("InvalidPrincipalException: {}", ex.getMessage());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "invalid_principal");
        body.put("error_description", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate",
                        "Bearer error=\"invalid_principal\", error_description=\"" + ex.getMessage() + "\"")
                .header("Cache-Control", "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @ExceptionHandler(NoActionPerformedException.class)
    public ProblemDetail handleNoActionPerformed(NoActionPerformedException ex) {
        var problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
        problemDetail.setTitle(messages.get("error.handler.noActionPerformed"));
        problemDetail.setDetail(ex.getReason());
        return problemDetail;
    }

}