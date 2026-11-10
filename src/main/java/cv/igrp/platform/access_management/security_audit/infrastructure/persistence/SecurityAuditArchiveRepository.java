package cv.igrp.platform.access_management.security_audit.infrastructure.persistence;

import cv.igrp.platform.access_management.security_audit.domain.entities.SecurityAuditArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecurityAuditArchiveRepository extends JpaRepository<SecurityAuditArchiveEntity, UUID> {

    /** The archived run that starts right after sequence {@code firstSequence - 1}, if any. */
    Optional<SecurityAuditArchiveEntity> findByFirstSequence(long firstSequence);
}
