package cv.igrp.platform.access_management.shared.security;

/**
 * v3-02 (#10): the route registration endpoint. Service tokens are admitted on
 * it like on {@code /api/m2m/**}: the session and M2M-rejection filters skip
 * it, and {@link ServiceAccountM2MAuthorizationFilter} requires a service
 * account with {@code igrp.m2m.sync}.
 */
public final class RouteRegistrationPaths {

    public static final String REGISTER = "/api/routes/register";

    private RouteRegistrationPaths() {
    }
}
