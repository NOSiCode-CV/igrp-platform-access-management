package cv.igrp.platform.access_management.routes.domain.service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The route key rules AM enforces on a registration (v3-02, spec "Key
 * convention"). The starter derives keys and already refuses to start on a bad
 * one; AM checks again so a misconfigured or hostile caller cannot write keys
 * under another service's prefix or with an action outside the closed set.
 */
public final class RouteKeys {

    /** The closed set of actions, from the method/ending table plus the other HTTP methods. */
    public static final Set<String> ACTIONS = Set.of(
            "list", "read", "create", "update-all", "update", "patch-all", "patch",
            "delete-all", "delete", "head", "options", "trace");

    /** The legacy permissions' namespace: AM 0.3 creates no new {@code igrp.*} permission (spec). */
    public static final String RESERVED_SERVICE = "igrp";

    private static final Pattern SERVICE = Pattern.compile("[A-Za-z0-9_-]{1,120}");
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_-]+");
    private static final int MAX_KEY_LENGTH = 255;

    private RouteKeys() {
    }

    public static boolean isValidService(String service) {
        return service != null && SERVICE.matcher(service).matches()
                && !service.equalsIgnoreCase(RESERVED_SERVICE);
    }

    /**
     * Why a display name cannot be used, or null when it can. A name in the
     * legacy {@code igrp.} namespace is refused: AM still looks legacy
     * permissions up by name, so a second row with that name would break them.
     */
    public static String nameProblem(String name) {
        if (name != null && name.regionMatches(true, 0, RESERVED_SERVICE + ".", 0, RESERVED_SERVICE.length() + 1)) {
            return "name " + name + " is in the reserved " + RESERVED_SERVICE + ". namespace";
        }
        return null;
    }

    /**
     * Why {@code key} cannot belong to {@code service}, or null when it can:
     * it must be {@code {service}.{resource...}.{action}} with at least one
     * resource segment and an action from {@link #ACTIONS}.
     */
    public static String problem(String service, String key) {
        if (key == null || key.isBlank()) {
            return "a route has no key";
        }
        if (key.length() > MAX_KEY_LENGTH) {
            return "key " + key + " is longer than " + MAX_KEY_LENGTH + " characters";
        }
        if (!key.startsWith(service + ".")) {
            return "key " + key + " does not start with the service name " + service + ".";
        }
        String[] segments = key.substring(service.length() + 1).split("\\.", -1);
        if (segments.length < 2) {
            return "key " + key + " needs a resource and an action";
        }
        for (String segment : segments) {
            if (!SEGMENT.matcher(segment).matches()) {
                return "key " + key + " has an empty or invalid segment";
            }
        }
        String action = segments[segments.length - 1];
        if (!ACTIONS.contains(action)) {
            return "key " + key + " ends in action " + action + ", which is not one of " + ACTIONS;
        }
        return null;
    }
}
