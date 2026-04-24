package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.AuthAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuthAuditLogJpaRepository extends JpaRepository<AuthAuditLogEntity, UUID> {

    List<AuthAuditLogEntity> findByUsernameOrderByTimestampDesc(String username);

    List<AuthAuditLogEntity> findAllByOrderByTimestampDesc();
}
