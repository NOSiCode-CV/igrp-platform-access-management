package cv.igrp.platform.access_management.security_audit.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One run of audit rows ({@code first_sequence..last_sequence}) that retention
 * wrote to object storage before deleting them from {@code t_security_audit_log}.
 *
 * <p>{@code previous_hash} is the chain hash just before the run and
 * {@code last_hash} the hash of its last row, so the validator can bridge the gap
 * the deletion leaves. The table is append-only (V18_1).
 */
@Entity
@Table(name = "t_security_audit_archive")
public class SecurityAuditArchiveEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "first_sequence", nullable = false, unique = true)
    private Long firstSequence;

    @Column(name = "last_sequence", nullable = false)
    private Long lastSequence;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    @Column(name = "row_count", nullable = false)
    private Long rowCount;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "cutoff", nullable = false)
    private LocalDateTime cutoff;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected SecurityAuditArchiveEntity() {
    }

    public SecurityAuditArchiveEntity(long firstSequence, long lastSequence, String previousHash, String lastHash,
                                      long rowCount, String storageKey, LocalDateTime cutoff) {
        this.id = UUID.randomUUID();
        this.firstSequence = firstSequence;
        this.lastSequence = lastSequence;
        this.previousHash = previousHash;
        this.lastHash = lastHash;
        this.rowCount = rowCount;
        this.storageKey = storageKey;
        this.cutoff = cutoff;
    }

    public UUID getId() {
        return id;
    }

    public Long getFirstSequence() {
        return firstSequence;
    }

    public Long getLastSequence() {
        return lastSequence;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getLastHash() {
        return lastHash;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public LocalDateTime getCutoff() {
        return cutoff;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
