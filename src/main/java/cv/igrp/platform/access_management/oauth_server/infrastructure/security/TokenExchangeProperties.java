package cv.igrp.platform.access_management.oauth_server.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Limits for token exchange actor chains. */
@Component
@ConfigurationProperties(prefix = "igrp.oauth2.token-exchange")
public class TokenExchangeProperties {
    private int maxActorDepth = 5;

    public int getMaxActorDepth() { return maxActorDepth; }
    public void setMaxActorDepth(int value) {
        if (value < 1 || value > 20) {
            throw new IllegalArgumentException("max-actor-depth must be between 1 and 20");
        }
        maxActorDepth = value;
    }
}
