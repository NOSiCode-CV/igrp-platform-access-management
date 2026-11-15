package cv.igrp.platform.access_management.central_audit.domain.service;

import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditChainReportDTO;
import cv.igrp.platform.access_management.central_audit.application.dto.BusinessAuditEventDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Checks a service's chain in the central store (v3-22, #32): every row's
 * previous hash must be the hash of the row before it, its hash must be what its
 * fields give, and the sequence numbers must have no gap. The first row that
 * fails is reported; every row after it is then unproven too.
 */
@Service
public class BusinessAuditValidator {

    private static final String ROWS = """
            SELECT id, service, sequence_number, event, resource, payload, username, agent, result, trace_id,
                   occurred_at, received_at, previous_hash, current_hash
              FROM t_business_audit_event WHERE service = ? ORDER BY sequence_number""";

    /** Rows read from the database at a time: a long chain is walked, never held whole. */
    static final int FETCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final BusinessAuditChain chain;

    public BusinessAuditValidator(DataSource dataSource, BusinessAuditChain chain) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setFetchSize(FETCH_SIZE);
        this.chain = chain;
    }

    @Transactional(readOnly = true)
    public BusinessAuditChainReportDTO validate(String service) {
        Walk walk = new Walk();
        // The read-only transaction keeps autocommit off, so PostgreSQL streams by the fetch size.
        jdbc.query(ROWS, (ResultSet rs) -> {
            while (walk.broken == null && rs.next()) {
                walk.check(rs);
            }
            return null;
        }, service);
        return new BusinessAuditChainReportDTO(service, walk.checked, walk.broken == null, walk.broken, walk.reason);
    }

    /** The walk along the chain; stops at the first broken row. */
    private final class Walk {
        private long checked;
        private String expectedPrevious = BusinessAuditChain.GENESIS;
        private Long broken;
        private String reason;

        void check(ResultSet rs) throws SQLException {
            long sequence = rs.getLong("sequence_number");
            checked++;
            BusinessAuditEventDTO event = new BusinessAuditEventDTO(rs.getObject("id", UUID.class),
                    rs.getString("event"), rs.getString("resource"), rs.getString("payload"),
                    rs.getString("username"), rs.getString("service"), rs.getString("agent"), rs.getString("result"),
                    rs.getString("trace_id"), rs.getTimestamp("occurred_at").toInstant());
            Instant received = rs.getTimestamp("received_at").toInstant();
            String previous = rs.getString("previous_hash");
            String current = rs.getString("current_hash");
            if (sequence != checked) {
                fail(sequence, "sequence gap: expected " + checked);
            } else if (!expectedPrevious.equals(previous)) {
                fail(sequence, "previous hash does not match the row before");
            } else if (!chain.hash(previous, sequence, event, received).equals(current)) {
                fail(sequence, "hash does not match the row's fields");
            }
            expectedPrevious = current;
        }

        private void fail(long sequence, String why) {
            broken = sequence;
            reason = why;
        }
    }
}
