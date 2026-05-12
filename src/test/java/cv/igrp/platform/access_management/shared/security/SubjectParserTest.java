package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase G1 / FR-13 — defensive parser for the JWT {@code sub} claim. Locks the
 * contract that a numeric subject is returned as {@code Integer}, anything else
 * raises {@link InvalidPrincipalException} (mapped to 401 by the global
 * exception handler) rather than leaking a {@link NumberFormatException} to
 * the caller.
 */
class SubjectParserTest {

    @Test
    void parsesNumericSubject() {
        assertEquals(3, SubjectParser.parseUserSubjectOrThrow("3"));
        assertEquals(123456, SubjectParser.parseUserSubjectOrThrow("123456"));
    }

    @Test
    void rejectsNullSubject() {
        InvalidPrincipalException ex = assertThrows(InvalidPrincipalException.class,
                () -> SubjectParser.parseUserSubjectOrThrow(null));
        assertEquals("missing_sub", ex.getMessage());
    }

    @Test
    void rejectsBlankSubject() {
        InvalidPrincipalException ex = assertThrows(InvalidPrincipalException.class,
                () -> SubjectParser.parseUserSubjectOrThrow("   "));
        assertEquals("missing_sub", ex.getMessage());
    }

    @Test
    void rejectsNonNumericSubject() {
        // The exact crash from production: client_credentials JWT sub == client_id.
        InvalidPrincipalException ex = assertThrows(InvalidPrincipalException.class,
                () -> SubjectParser.parseUserSubjectOrThrow("igrp-access-management"));
        assertEquals("non_numeric_sub", ex.getMessage());
    }
}
