package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.access_management.shared.infrastructure.correlation.CorrelationIds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v3-24 (#31): one correlation id per request joins AM's audit rows, its Envers
 * revisions and the response. With tracing on it is the trace id (covered at
 * unit level: the compat suite runs with the OTel SDK off); here, the
 * {@code X-Correlation-ID} fallback and the random id.
 */
class TraceCorrelationIT extends AbstractCompatIT {

    private static final String HEADER = CorrelationIds.HEADER;

    /** The Envers revision of the department with this code, with who created it. */
    private static final String REVISION_OF_DEPARTMENT = """
            SELECT r.username, r.trace_id, d.created_by FROM t_department d
              JOIN t_department_aud a ON a.id = d.id
              JOIN revinfo r ON r.rev = a.rev
             WHERE d.code = ?""";

    @Autowired
    ApplicationContext context;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("X-Correlation-ID is echoed and stored in the request's audit row and Envers revision")
    void headerJoinsAuditRowAndRevision() {
        String correlationId = "it-" + UUID.randomUUID().toString().replace("-", "");
        String code = "TRC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HttpResponse<String> response = createDepartment(code, correlationId);

        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        assertThat(response.headers().firstValue(HEADER)).contains(correlationId);

        Map<String, Object> revision = jdbc.queryForMap(REVISION_OF_DEPARTMENT, code);
        assertThat(revision.get("created_by")).isNotNull();
        assertThat(revision)
                .containsEntry("trace_id", correlationId)
                .containsEntry("username", revision.get("created_by"));

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE correlation_id = ? AND entity_name IS NOT NULL",
                Long.class, correlationId)).as("the settings audit row of the change").isPositive();
    }

    @Test
    @DisplayName("An invalid X-Correlation-ID is replaced by a random id everywhere")
    void invalidHeaderIsReplaced() {
        String code = "TRC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HttpResponse<String> response = createDepartment(code, "forged\tid; DROP");

        assertRandomIdEverywhere(code, response);
    }

    @Test
    @DisplayName("Without X-Correlation-ID the request gets a random id, shared by its audit row and revision")
    void missingHeaderGetsARandomId() {
        String code = "TRC_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        HttpResponse<String> response = createDepartment(code, null);

        assertRandomIdEverywhere(code, response);
    }

    private void assertRandomIdEverywhere(String code, HttpResponse<String> response) {
        String echoed = response.headers().firstValue(HEADER).orElseThrow();
        assertThat(echoed).matches("[0-9a-f]{32}");
        assertThat(jdbc.queryForMap(REVISION_OF_DEPARTMENT, code)).containsEntry("trace_id", echoed);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM t_security_audit_log WHERE correlation_id = ?", Long.class, echoed))
                .as("the request's audit row").isPositive();
    }

    @Test
    @DisplayName("V19_1 is idempotent: applying it again changes nothing")
    void migrationIsIdempotent() throws Exception {
        String sql = new ClassPathResource("db/migration/V19_1__revinfo_username_and_trace_id.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        jdbc.execute(sql);

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_name = 'revinfo' AND column_name IN ('username', 'trace_id')""", Long.class))
                .isEqualTo(2);
    }

    private HttpResponse<String> createDepartment(String code, String correlationId) {
        CompatSupport api = new CompatSupport(context);
        return api.postJson("/api/departments", api.superadminToken(),
                "{\"code\":" + CompatSupport.q(code) + ",\"name\":\"Trace " + code + "\",\"status\":\"ACTIVE\"}",
                correlationId == null ? Map.of() : Map.of(HEADER, correlationId));
    }
}
