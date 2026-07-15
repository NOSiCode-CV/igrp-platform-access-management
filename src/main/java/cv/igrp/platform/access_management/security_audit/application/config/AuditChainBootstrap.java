package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditChainService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup guards for the audit hash chain.
 *
 * <ul>
 *   <li><b>Secret enforcement (N8):</b> {@code AUDIT_CHAIN_SECRET}
 *       ({@code igrp.audit.chain.secret}) is mandatory under the {@code prod}
 *       profile — a blank value fails fast at startup.</li>
 *   <li><b>Rehash-on-boot (R2.6 / N9):</b> when
 *       {@code igrp.audit.chain.rehash-on-boot=true} and the active profile is
 *       not production, the whole chain is recomputed once on
 *       {@link ApplicationReadyEvent} (used after a schema change widens the
 *       hash input). The flag is refused — logged and ignored — under prod.</li>
 * </ul>
 */
@Component
public class AuditChainBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AuditChainBootstrap.class);

    private final Environment environment;
    private final SecurityAuditChainService chainService;

    @Value("${igrp.audit.chain.secret:}")
    private String chainSecret;

    @Value("${igrp.audit.chain.rehash-on-boot:false}")
    private boolean rehashOnBoot;

    public AuditChainBootstrap(Environment environment, SecurityAuditChainService chainService) {
        this.environment = environment;
        this.chainService = chainService;
    }

    @PostConstruct
    void validateSecret() {
        if (isProductionProfile() && (chainSecret == null || chainSecret.isBlank())) {
            throw new IllegalStateException(
                    "AUDIT_CHAIN_SECRET (igrp.audit.chain.secret) is required under the production profile "
                            + "but is not set. Refusing to start to preserve audit tamper-evidence.");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void rehashChainIfRequested() {
        if (!rehashOnBoot) {
            return;
        }
        if (isProductionProfile()) {
            log.error("[AUDIT] igrp.audit.chain.rehash-on-boot=true is refused under the production profile; "
                    + "ignoring (the production chain is never rewritten).");
            return;
        }
        log.warn("[AUDIT] rehash-on-boot enabled — recomputing the entire audit hash chain. "
                + "This is a dev/staging-only operation.");
        int rewritten = chainService.rehashAll();
        log.warn("[AUDIT] rehash-on-boot complete — {} rows rewritten.", rewritten);
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
