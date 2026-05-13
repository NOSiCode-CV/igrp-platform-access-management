package cv.igrp.platform.access_management.shared.security;

import java.util.UUID;

/**
 * Phase G2 — safe parser for the JWT {@code sub} claim when it is
 * expected to identify a user id (UUID string).
 *
 * <p>After G2 the user PK is a String UUID. M2M tokens carry a non-UUID
 * {@code sub} (the client_id); those throw {@link InvalidPrincipalException}
 * which the global exception handler turns into HTTP 401.
 */
public final class SubjectParser {

    private SubjectParser() {
    }

    /**
     * Parse a JWT {@code sub} claim that is expected to be a UUID user id.
     *
     * @param sub the raw subject claim from the JWT
     * @return the validated user id (UUID string)
     * @throws InvalidPrincipalException if {@code sub} is null, blank, or not
     *         a valid UUID (the M2M case)
     */
    public static String parseUserSubjectOrThrow(String sub) {
        if (sub == null || sub.isBlank()) {
            throw new InvalidPrincipalException("missing_sub");
        }
        try {
            UUID.fromString(sub);
        } catch (IllegalArgumentException ex) {
            throw new InvalidPrincipalException("non_uuid_sub", ex);
        }
        return sub;
    }

    /**
     * Alias for {@link #parseUserSubjectOrThrow(String)}; same semantics.
     */
    public static String requireUserSub(String sub) {
        return parseUserSubjectOrThrow(sub);
    }
}
