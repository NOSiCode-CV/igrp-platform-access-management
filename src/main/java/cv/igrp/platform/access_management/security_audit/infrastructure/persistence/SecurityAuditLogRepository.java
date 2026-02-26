package cv.igrp.platform.access_management.security_audit.infrastructure.persistence;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link SecurityAuditLogEntity}.
 * This interface provides the necessary methods for database operations on audit logs,
 * such as saving and retrieving them.
 */
@Repository
public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLogEntity, Long> {
}