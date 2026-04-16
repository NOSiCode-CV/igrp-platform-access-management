package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.OtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpEntityRepository extends JpaRepository<OtpEntity, Long> {
    Optional<OtpEntity> findFirstByReferenceIdAndStatusOrderByCreatedDateDesc(String referenceId, String status);
}
