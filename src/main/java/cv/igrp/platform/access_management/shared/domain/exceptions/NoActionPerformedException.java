package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NoActionPerformedException extends ResponseStatusException {

    public NoActionPerformedException(String reason) {
        super(HttpStatus.OK, reason);
    }
}
