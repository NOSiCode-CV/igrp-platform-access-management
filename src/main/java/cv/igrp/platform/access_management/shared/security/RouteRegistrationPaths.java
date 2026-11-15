package cv.igrp.platform.access_management.shared.security;

/**
 * v3-02 (#10): the route registration endpoint. Service tokens are admitted on
 * it like on {@code /api/m2m/**}: the session filter skips it. Like every AM
 * route, it needs its key (#36).
 */
public final class RouteRegistrationPaths {

    public static final String REGISTER = "/api/routes/register";

    private RouteRegistrationPaths() {
    }
}
