package cv.igrp.platform.access_management.shared.domain.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of validating a resource.
 *
 * <p>This model holds a validation status and a list of failure messages in case the validation
 * fails. It is typically used to communicate the outcome of business rule checks before persisting
 * a {@code Role} entity.
 */
public class ResourceValidationResponse {

    /** Indicates whether the validation passed successfully. */
    private boolean isValid;
    /** Contains messages describing validation failures, if any. */
    private List<String> failureMessage;

    public ResourceValidationResponse() {
    }

    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public List<String> getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(List<String> failureMessage) {
        this.failureMessage = failureMessage;
    }

    /**
     * Adds a single failure message to the list. Initializes the list if it is {@code null}.
     *
     * @param message the failure message to add
     */
    public void addFailureMessage(String message) {
        if (failureMessage == null)
            this.failureMessage = new ArrayList<>();
        failureMessage.add(message);
    }
}
