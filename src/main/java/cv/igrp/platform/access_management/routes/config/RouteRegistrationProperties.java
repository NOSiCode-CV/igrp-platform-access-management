package cv.igrp.platform.access_management.routes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Route registration (v3-03, #11): {@code igrp.routes.deletion-grace}
 * ({@code IGRP_ROUTES_DELETION_GRACE}), how long after the newest version of a
 * service stops declaring a key the key is deleted. 15 minutes by default: a
 * rolling deploy has replaced its pods by then.
 */
@ConfigurationProperties("igrp.routes")
public class RouteRegistrationProperties {

    private Duration deletionGrace = Duration.ofMinutes(15);

    public Duration getDeletionGrace() {
        return deletionGrace;
    }

    public void setDeletionGrace(Duration deletionGrace) {
        if (deletionGrace == null || deletionGrace.isNegative()) {
            throw new IllegalArgumentException("igrp.routes.deletion-grace must not be negative, was " + deletionGrace);
        }
        this.deletionGrace = deletionGrace;
    }
}
