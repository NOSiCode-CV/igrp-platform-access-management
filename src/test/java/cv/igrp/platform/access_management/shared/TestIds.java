package cv.igrp.platform.access_management.shared;

/**
 * Canonical UUID-shaped user identifiers for tests.
 *
 * <p>Pattern: {@code 00000000-0000-0000-0000-{12-digit-zero-padded-n}} so that
 * legacy {@code int} test ids translate to readable UUIDs (id=1 -> ...01).
 */
public final class TestIds {

    public static final String USER_1 = user(1);
    public static final String USER_2 = user(2);
    public static final String USER_3 = user(3);
    public static final String USER_42 = user(42);

    public static String user(int n) {
        return String.format("00000000-0000-0000-0000-%012d", n);
    }

    public static String user(long n) {
        return String.format("00000000-0000-0000-0000-%012d", n);
    }

    private TestIds() {}
}
