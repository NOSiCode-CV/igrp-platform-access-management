package cv.igrp.platform.access_management.central_audit.domain.service;

import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditRowDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Searches the central store of business audit events (v3-22, #32). */
@Service
public class BusinessAuditQueries {

    private static final int MAX_SIZE = 200;

    private static final String SEARCH = """
            SELECT id, service, sequence_number, event, resource, payload, username, agent, result, trace_id,
                   occurred_at, received_at
              FROM t_business_audit_event
             WHERE (CAST(? AS varchar) IS NULL OR service = ?)
               AND (CAST(? AS varchar) IS NULL OR username = ?)
               AND (CAST(? AS varchar) IS NULL OR event = ?)
               AND (CAST(? AS varchar) IS NULL OR resource = ?)
               AND (CAST(? AS varchar) IS NULL OR trace_id = ?)
               AND (CAST(? AS timestamptz) IS NULL OR occurred_at >= ?)
               AND (CAST(? AS timestamptz) IS NULL OR occurred_at < ?)
             ORDER BY occurred_at DESC, id
             LIMIT ? OFFSET ?""";

    /** Every field optional. {@code to} is exclusive. */
    public record Filter(String service, String username, String event, String resource, String traceId,
                         Instant from, Instant to) {
    }

    private final JdbcTemplate jdbc;

    public BusinessAuditQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<BusinessAuditRowDTO> search(Filter filter, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw IgrpResponseStatusException.badRequest("page must be >= 0 and size between 1 and " + MAX_SIZE);
        }
        Timestamp from = filter.from() == null ? null : Timestamp.from(filter.from());
        Timestamp to = filter.to() == null ? null : Timestamp.from(filter.to());
        return jdbc.query(SEARCH, (rs, i) -> new BusinessAuditRowDTO(rs.getObject("id", UUID.class),
                        rs.getString("service"), rs.getLong("sequence_number"), rs.getString("event"),
                        rs.getString("resource"), rs.getString("payload"), rs.getString("username"),
                        rs.getString("agent"), rs.getString("result"), rs.getString("trace_id"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("received_at").toInstant()),
                filter.service(), filter.service(), filter.username(), filter.username(), filter.event(),
                filter.event(), filter.resource(), filter.resource(), filter.traceId(), filter.traceId(), from, from,
                to, to, size, (long) page * size);
    }
}
