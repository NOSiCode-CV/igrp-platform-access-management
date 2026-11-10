package cv.igrp.platform.access_management.security_audit.application.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup guard for the audit hash chain.
 *
 * <p><b>Secret enforcement (N8):</b> {@code AUDIT_CHAIN_SECRET}
 * ({@code igrp.audit.chain.secret}) is mandatory under the {@code prod}
 * profile — a blank value fails fast at startup.
 *
 * <p>The chain is never rewritten: {@code t_security_audit_log} refuses every
 * UPDATE (V18_1), so the former rehash-on-boot option is gone.
 */
@Component
public class AuditChainBootstrap {

    private final Environment environment;

    @Value("${igrp.audit.chain.secret:}")
    private String chainSecret;

    public AuditChainBootstrap(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateSecret() {
        if (isProductionProfile() && (chainSecret == null || chainSecret.isBlank())) {
            throw new IllegalStateException(
                    "AUDIT_CHAIN_SECRET (igrp.audit.chain.secret) is required under the production profile "
                            + "but is not set. Refusing to start to preserve audit tamper-evidence.");
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }
}
