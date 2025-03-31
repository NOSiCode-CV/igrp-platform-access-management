package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IgrpResponseStatusException.class)
    public ResponseEntity<Object> handleIgrpException(IgrpResponseStatusException ex) {
        IgrpProblem<?> problem = ex.getProblem();
        return ResponseEntity
                .status(problem.getStatus())
                .body(problem);
    }

}