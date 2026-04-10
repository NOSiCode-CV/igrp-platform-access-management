package cv.igrp.platform.access_management.shared.infrastructure.persistence.repository;

import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;


@Repository
public interface InvitationEntityRepository extends
        JpaRepository<InvitationEntity, Integer>,
        JpaSpecificationExecutor<InvitationEntity> {

    default InvitationEntity findByIdOrThrow(Integer id) {
        return this.findById(id)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "InvitationEntity not found for id: " + id));
    }

    Optional<InvitationEntity> findByTokenAndStatus(String token, InvitationStatus status);

    Optional<InvitationEntity> findByToken(String token);

    default InvitationEntity findByTokenOrThrow(String token) {
        return this.findByToken(token)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "InvitationEntity not found for token: " + token));
    }

    default InvitationEntity findByTokenAndStatusPending(String token) {
        return this.findByTokenAndStatus(token, InvitationStatus.PENDING)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "InvitationEntity not found for token: " + token));
    }
    /**
     * Find all invitations ordered by last modified date in descending order.
     *
     * @return a list of invitation entities ordered by last modified date in descending order
     */
    List<InvitationEntity> findAllByStatusNotOrderByLastModifiedDateDesc(InvitationStatus status);

    @Query(value = """
    SELECT i.*
    FROM t_invitation_entity i
    WHERE i.status <> :excludedStatus
      AND (:identifierValue IS NULL OR i.identifier_value ILIKE CONCAT('%', :identifierValue, '%'))
    ORDER BY i.last_modified_date DESC
""", nativeQuery = true)
    List<InvitationEntity> findAllByStatusNotOrderByLastModifiedDateDescFiltered(
            @Param("excludedStatus") String excludedStatus,
            @Param("identifierValue") String identifierValue
    );

    Optional<InvitationEntity> findByIdentifierTypeAndIdentifierValueAndStatus(IdentifierType identifierType, String identifierValue, InvitationStatus status);

}