package cv.igrp.platform.access_management.routes.domain.service;

/**
 * Orders the versions services report at registration (v3-02), so an older
 * replica that restarts during a rolling deploy cannot move the "last seen in
 * version" marker back.
 *
 * <p>Semver-like: the dotted core ({@code 1.10.0}) compares number by number
 * (missing parts count as 0); with equal cores a version without a
 * pre-release tag ({@code 1.4.0}) is newer than one with it
 * ({@code 1.4.0-beta.1}), and two tags compare as text. Build metadata after
 * {@code +} is ignored. Non-numeric core parts compare as text.
 */
public final class ServiceVersions {

    private ServiceVersions() {
    }

    /** Negative, zero or positive as {@code a} is older than, as old as, or newer than {@code b}. */
    public static int compare(String a, String b) {
        String[] left = split(a);
        String[] right = split(b);
        int core = compareCore(left[0], right[0]);
        if (core != 0) {
            return core;
        }
        if (left[1] == null || right[1] == null) {
            return left[1] == null ? (right[1] == null ? 0 : 1) : -1;
        }
        return left[1].compareTo(right[1]);
    }

    /** [core, pre-release or null], without build metadata. */
    private static String[] split(String version) {
        String value = version == null ? "" : version.strip();
        int build = value.indexOf('+');
        if (build >= 0) {
            value = value.substring(0, build);
        }
        int pre = value.indexOf('-');
        return pre >= 0
                ? new String[]{value.substring(0, pre), value.substring(pre + 1)}
                : new String[]{value, null};
    }

    private static int compareCore(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            int result = isNumber(l) && isNumber(r)
                    ? Long.compare(Long.parseLong(l), Long.parseLong(r))
                    : l.compareTo(r);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static boolean isNumber(String part) {
        return !part.isEmpty() && part.length() <= 18 && part.chars().allMatch(Character::isDigit);
    }
}
