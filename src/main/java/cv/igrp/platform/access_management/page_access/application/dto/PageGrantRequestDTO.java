package cv.igrp.platform.access_management.page_access.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Grant a page to a role (v3-27, #35): its required keys, plus the optional
 * ones the administrator chose.
 *
 * @param page     the page key
 * @param optional the chosen optional keys; each must be one of the page's
 */
public record PageGrantRequestDTO(@NotBlank(message = "The field <page> is required") String page,
                                  List<@NotBlank String> optional) {

    public List<String> optionalKeys() {
        return optional == null ? List.of() : optional;
    }
}
