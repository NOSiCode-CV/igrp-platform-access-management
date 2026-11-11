package cv.igrp.platform.access_management.shared.infrastructure.persistence.entity;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.revision.RevisionInfoListener;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * AM's Envers revision (v3-24): the default {@code revinfo} ({@code rev},
 * {@code revtstmp}, sequence {@code revinfo_seq} by 50, as in base-schema.sql)
 * plus the username of the change and the request's correlation id (V19_1).
 *
 * <p>Same shape as the 0.3 starter's {@code IgrpRevision}; AM keeps its own
 * until it adopts the starter (decision on #8).
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(RevisionInfoListener.class)
public class RevisionInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq_generator")
    @SequenceGenerator(name = "revinfo_seq_generator", sequenceName = "revinfo_seq", allocationSize = 50)
    @RevisionNumber
    @Column(name = "rev")
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;

    @Column(name = "username")
    private String username;

    /** The request's correlation id: the OTel trace id, or the {@code X-Correlation-ID} fallback. */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    public int getRev() {
        return rev;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
