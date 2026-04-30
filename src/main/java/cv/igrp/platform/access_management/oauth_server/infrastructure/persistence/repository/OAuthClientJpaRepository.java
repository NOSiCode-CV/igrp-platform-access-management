package cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthClientJpaRepository extends JpaRepository<OAuthClientEntity, UUID> {

    Optional<OAuthClientEntity> findByClientId(String clientId);

    boolean existsByClientId(String clientId);
}
