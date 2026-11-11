package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.security_audit.application.service.AuditReportContext;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * v3-23 (#30) — AM's audit trail is immutable and never fails silently.
 *
 * <p>Runs against the real schema (Flyway migrations on Postgres), because
 * append-only is enforced by the database, not by application code.
 */
class AuditHardeningIT extends AbstractCompatIT {

    @Autowired
    SecurityAuditService auditService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    ApplicationContext context;
    @Autowired
    ApplicationEventPublisher events;
    @Autowired
    MeterRegistry meters;

    @Test
    @DisplayName("An audit row cannot be updated, not even with the former app.audit_purge bypass")
    void updateIsRefusedEvenWithFormerBypass() {
        String marker = recordDenial();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.audit_purge = 'true'");
            jdbc.update("UPDATE t_security_audit_log SET username = 'tampered' WHERE decision_reason = ?", marker);
        })).isInstanceOf(DataAccessException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE decision_reason = ? AND username = 'tampered'",
                Long.class, marker)).isZero();
    }

    @Test
    @DisplayName("A recent audit row cannot be deleted, not even with the former app.audit_purge bypass")
    void deleteOfRecentRowIsRefused() {
        String marker = recordDenial();

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL app.audit_purge = 'true'");
            jdbc.update("DELETE FROM t_security_audit_log WHERE decision_reason = ?", marker);
        })).isInstanceOf(DataAccessException.class);

        assertThat(countByReason(marker)).isOne();
    }

    @Test
    @DisplayName("The purge endpoint no longer exists, even for the superadmin")
    void purgeEndpointIsGone() {
        CompatSupport api = new CompatSupport(context);
        String marker = recordDenial();

        HttpResponse<String> response = api.delete("/api/auth/audit/purge", api.superadminToken());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
        assertThat(countByReason(marker)).isOne();
    }

    @Test
    @DisplayName("A settings change that rolls back leaves no audit row")
    void rolledBackSettingsChangeIsNotAudited() {
        String role = "it-role-" + UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            events.publishEvent(new RoleCreatedEvent(role));
            status.setRollbackOnly();
        });
        // No wait is needed: the write is synchronous, and a listener that went
        // async again would fail committedSettingsChangeIsAuditedInTheSameTransaction.

        assertThat(countByEntityName(role)).isZero();
    }

    @Test
    @DisplayName("A committed settings change is audited by the time the commit returns")
    void committedSettingsChangeIsAuditedInTheSameTransaction() {
        String role = "it-role-" + UUID.randomUUID();

        tx.executeWithoutResult(status -> events.publishEvent(new RoleCreatedEvent(role)));

        // No wait: the row commits with the change, not on a worker thread later.
        assertThat(countByEntityName(role)).isOne();
    }

    @Test
    @DisplayName("An access event after a settings change in the same transaction does not deadlock")
    void accessEventAfterSettingsChangeDoesNotDeadlock() {
        // A role change audits the change, then revokes the user's sessions,
        // which writes an access event in its own transaction (AddRolesToUser).
        String role = "it-role-" + UUID.randomUUID();
        String marker = "it-" + UUID.randomUUID();

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> tx.executeWithoutResult(status -> {
            events.publishEvent(new RoleCreatedEvent(role));
            auditService.logAccessDenied("it.audit.read", marker);
        }));

        assertThat(countByEntityName(role)).isOne();
        assertThat(countByReason(marker)).isOne();
    }

    @Test
    @DisplayName("An access denial is recorded even when the surrounding transaction rolls back")
    void denialSurvivesRollback() {
        String marker = "it-" + UUID.randomUUID();

        tx.executeWithoutResult(status -> {
            auditService.logAccessDenied("it.audit.read", marker);
            status.setRollbackOnly();
        });

        assertThat(countByReason(marker)).isOne();
    }

    @Test
    @DisplayName("An access event the database rejects is counted, not rethrown")
    void rejectedAccessEventIsCountedNotRethrown() {
        String marker = "it-" + UUID.randomUUID();
        double before = accessFailures();

        // access_role is varchar(255): the insert fails in the database itself.
        assertThatCode(() -> auditService.logEvent(AuditEventType.ACCESS_DENIED, AuditCategory.AUTHORIZATION,
                Map.of("decisionReason", marker),
                AuditReportContext.builder().accessRole("r".repeat(300)).build()))
                .doesNotThrowAnyException();

        assertThat(countByReason(marker)).isZero();
        assertThat(accessFailures()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("V18_1 is idempotent: applying it again on a migrated database changes nothing")
    void migrationIsIdempotent() throws Exception {
        String sql = new ClassPathResource("db/migration/V18_1__security_audit_append_only_and_retention.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        jdbc.execute(sql);

        String marker = recordDenial();
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE t_security_audit_log SET username = 'tampered' WHERE decision_reason = ?", marker))
                .isInstanceOf(DataAccessException.class);
    }

    private double accessFailures() {
        Counter counter = meters.find("igrp.audit.write.failures").tag("path", "access").counter();
        return counter == null ? 0.0 : counter.count();
    }

    private long countByEntityName(String entityName) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE entity_name = ?", Long.class, entityName);
    }

    private long countByReason(String marker) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE decision_reason = ?", Long.class, marker);
    }

    /** Writes one denial with a unique reason and returns that reason, to find the row again. */
    private String recordDenial() {
        String marker = "it-" + UUID.randomUUID();
        auditService.logAccessDenied("it.audit.read", marker);
        return marker;
    }
}
