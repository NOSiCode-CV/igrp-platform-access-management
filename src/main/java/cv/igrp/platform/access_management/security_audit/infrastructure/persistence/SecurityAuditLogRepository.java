package cv.igrp.platform.access_management.security_audit.infrastructure.persistence;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SecurityAuditLogEntity}.
 * This interface provides the necessary methods for database operations on audit logs,
 * such as saving and retrieving them.
 *
 * <p>The primary key is a {@link UUID} (Phase 1 of the Unified Audit &amp; Reports
 * feature). {@link JpaSpecificationExecutor} backs the filtered list endpoint.
 */
@Repository
public interface SecurityAuditLogRepository
        extends JpaRepository<SecurityAuditLogEntity, UUID>, JpaSpecificationExecutor<SecurityAuditLogEntity> {

    Page<SecurityAuditLogEntity> findByUserId(String userId, Pageable pageable);

    /** Current chain tip — the row with the highest sequence number. */
    Optional<SecurityAuditLogEntity> findTopByOrderBySequenceNumberDesc();
}
