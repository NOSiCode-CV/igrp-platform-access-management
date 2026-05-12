package cv.igrp.platform.access_management.shared.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Phase G1 / FR-13 — typed AuthenticationException raised when a JWT principal
 * cannot be turned into a usable user identity (missing {@code sub}, blank
 * {@code sub}, or a {@code sub} that does not represent a real user — e.g. an
 * M2M client_id that reached a user-scoped path).
 *
 * <p>Mapped to HTTP 401 by the global exception handler so the historical
 * {@link NumberFormatException} from {@code Integer.parseInt(jwt.getSubject())}
 * is no longer reachable.
 */
public class InvalidPrincipalException extends AuthenticationException {

    public InvalidPrincipalException(String message) {
        super(message);
    }

    public InvalidPrincipalException(String message, Throwable cause) {
        super(message, cause);
    }
}
