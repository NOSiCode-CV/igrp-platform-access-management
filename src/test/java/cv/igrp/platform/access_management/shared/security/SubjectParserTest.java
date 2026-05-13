package cv.igrp.platform.access_management.shared.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Phase G2 — defensive parser for the JWT {@code sub} claim. The user-PK is a
 * UUID string after G2 so {@link SubjectParser} validates the {@code sub} is a
 * UUID; anything else raises {@link InvalidPrincipalException} (mapped to 401
 * by the global exception handler).
 */
class SubjectParserTest {

    @Test
    void parsesUuidSubject() {
        String uuid = "00000000-0000-0000-0000-000000000003";
        assertEquals(uuid, SubjectParser.parseUserSubjectOrThrow(uuid));
        String uuid2 = "12345678-1234-1234-1234-123456789012";
        assertEquals(uuid2, SubjectParser.parseUserSubjectOrThrow(uuid2));
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
    void rejectsNonUuidSubject() {
        // The exact crash from production: client_credentials JWT sub == client_id.
        InvalidPrincipalException ex = assertThrows(InvalidPrincipalException.class,
                () -> SubjectParser.parseUserSubjectOrThrow("igrp-access-management"));
        assertEquals("non_uuid_sub", ex.getMessage());
    }
}
