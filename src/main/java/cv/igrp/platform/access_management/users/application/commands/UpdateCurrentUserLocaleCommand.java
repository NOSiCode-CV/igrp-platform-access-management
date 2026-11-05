package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code PUT /api/users/me/locale} — body {@code {"locale": "fr"}}.
 *
 * <p>Stores the current user's language preference in {@code metadata.locale}.
 * The value is validated by the handler (not by Bean Validation) so that every
 * invalid value — missing, blank or unsupported — yields the same
 * {@code 400 INVALID_LOCALE} error.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCurrentUserLocaleCommand implements Command {

    /** Platform language tag: {@code pt}, {@code en} or {@code fr} (regional variants are normalized). */
    private String locale;

}
