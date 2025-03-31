package cv.igrp.platform.access_management.shared.domain.exceptions;

import org.springframework.http.HttpStatus;

public class IgrpProblem<T> {

    private final HttpStatus status;
    private final String title;
    private final T details;

    public IgrpProblem(HttpStatus status, String title, T details) {
        this.status = status;
        this.title = title;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public T getDetails() {
        return details;
    }

}