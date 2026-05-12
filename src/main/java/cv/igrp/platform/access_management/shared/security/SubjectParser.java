package cv.igrp.platform.access_management.shared.security;

/**
 * Phase G1 / FR-13 — safe parser for the JWT {@code sub} claim when it is
 * expected to identify a numeric user id.
 *
 * <p>Replaces the historical pattern {@code Integer.parseInt(jwt.getSubject())}
 * which crashes with {@link NumberFormatException} when the bearer token is
 * a {@code client_credentials} (M2M) JWT whose {@code sub} equals the
 * {@code client_id}. Instead of crashing this helper throws a typed
 * {@link InvalidPrincipalException} that the global exception handler turns
 * into a clean HTTP 401.
 *
 * <p>In G1 the helper remains {@link Integer}-typed because the user PK is
 * still {@code Integer}. G2 rewrites the signature to {@link String} when the
 * user PK migrates to UUID.
 */
public final class SubjectParser {

    private SubjectParser() {
    }

    /**
     * Parse a JWT {@code sub} claim that is expected to be a numeric user id.
     *
     * @param sub the raw subject claim from the JWT
     * @return the parsed numeric user id
     * @throws InvalidPrincipalException if {@code sub} is null, blank, or not
     *         a base-10 integer (the M2M case)
     */
    public static Integer parseUserSubjectOrThrow(String sub) {
        if (sub == null || sub.isBlank()) {
            throw new InvalidPrincipalException("missing_sub");
        }
        try {
            return Integer.parseInt(sub);
        } catch (NumberFormatException ex) {
            throw new InvalidPrincipalException("non_numeric_sub", ex);
        }
    }
}
