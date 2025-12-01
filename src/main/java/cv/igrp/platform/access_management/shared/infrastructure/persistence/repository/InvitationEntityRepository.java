package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;


@Repository
public interface InvitationEntityRepository extends
        JpaRepository<InvitationEntity, Integer>,
        JpaSpecificationExecutor<InvitationEntity> {

    default InvitationEntity findByIdOrThrow(Integer id) {
        return this.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "InvitationEntity not found for id: " + id));
    }

    Optional<InvitationEntity> findByTokenAndStatus(String token, InvitationStatus status);

    default InvitationEntity findByTokenAndStatusPending(String token) {
        return this.findByTokenAndStatus(token, InvitationStatus.PENDING)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "InvitationEntity not found for token: " + token));
    }
    /**
     * Find all invitations ordered by last modified date in descending order.
     *
     * @return a list of invitation entities ordered by last modified date in descending order
     */
    List<InvitationEntity> findAllByOrderByLastModifiedDateDesc();

}