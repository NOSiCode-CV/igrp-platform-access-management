package cv.igrp.platform.access_management.security_audit.application.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code igrp.audit.retention.*} settings (v3-23).
 *
 * <ul>
 *   <li>{@code days} — how long audit rows stay in {@code t_security_audit_log}
 *       before retention archives them to storage and deletes them. Default 1825
 *       (5 years). Anything below {@value #MINIMUM_DAYS} fails startup; the
 *       database enforces the same floor (V18_1).</li>
 *   <li>{@code batch-size} — the most rows one retention run archives.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "igrp.audit.retention")
public class AuditRetentionProperties implements InitializingBean {

    /** Kept in step with the floor in the V18_1 trigger. */
    public static final int MINIMUM_DAYS = 365;

    private int days = 1825;

    private int batchSize = 10_000;

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public void afterPropertiesSet() {
        if (days < MINIMUM_DAYS) {
            throw new IllegalStateException("igrp.audit.retention.days is " + days
                    + "; audit rows must be kept at least " + MINIMUM_DAYS + " days");
        }
        if (batchSize < 1) {
            throw new IllegalStateException("igrp.audit.retention.batch-size must be at least 1, was " + batchSize);
        }
    }
}
