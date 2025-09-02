package cv.igrp.platform.access_management.shared.domain.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IgrpResponseStatusException.class)
    public ProblemDetail handleIgrpResponseStatusException(IgrpResponseStatusException ex) {

        LOGGER.error(ex.getMessage(), ex);

        return ex.getBody();
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
                        FieldError::getField, fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value")
                );

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation Errors");
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {

        var errors = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));

        var problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Constraint Violation Errors");
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

            problem.setTitle("Invalid value for enum type: " + targetType.getSimpleName());
            problem.setProperty("CurrentValue", ife.getValue());
            problem.setProperty("AllowedValues", allowedValues);
            return problem;
        }

        problem.setTitle("Malformed JSON request");
        problem.setDetail(ex.getMessage());

        return problem;
    }

    @ExceptionHandler(NoActionPerformedException.class)
    public ProblemDetail handleNoActionPerformed(NoActionPerformedException ex) {
        var problemDetail = ProblemDetail.forStatus(ex.getStatusCode());
        problemDetail.setTitle("No Action Performed");
        problemDetail.setDetail(ex.getReason());
        return problemDetail;
    }

}