package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserIdentifierEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentifierEntityRepository extends JpaRepository<UserIdentifierEntity, Integer> {

    Optional<UserIdentifierEntity> findByTypeAndValueNormalized(String type, String valueNormalized);

}
