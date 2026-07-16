package cv.igrp.platform.access_management.security_audit.infrastructure.persistence;

import cv.igrp.platform.access_management.security_audit.domain.entities.AuditReportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditReportFileEntity} — the archive of
 * generated report exports. {@link JpaSpecificationExecutor} backs the filtered
 * list endpoint.
 */
@Repository
public interface AuditReportFileRepository
        extends JpaRepository<AuditReportFileEntity, UUID>, JpaSpecificationExecutor<AuditReportFileEntity> {
}
