package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.AccessHistoryEntity;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.history.RevisionRepository;

@Repository
public interface AccessHistoryEntityRepository extends
        JpaRepository<AccessHistoryEntity, Integer>,
        JpaSpecificationExecutor<AccessHistoryEntity>,
        RevisionRepository<AccessHistoryEntity, Integer, Integer> {

    default AccessHistoryEntity findByIdOrThrow(Integer id) {
        return this.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(IgrpErrorCode.IGRP_AUTH_ACCESS_HISTORY_NOT_FOUND_BY_ID, id));
    }

    /**
     * Finds an access history by user and application.
     *
     * @param user        the user ID
     * @param application the application entity
     * @return the @link{AccessHistoryEntity} if found, otherwise empty
     */
    Optional<AccessHistoryEntity> findByUserIdAndApplication(Integer user, ApplicationEntity application);

    List<AccessHistoryEntity> findByUserIdOrderByLastAccessDesc(Integer userId);

}