package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

@Component
public class GetUserInvitationsQueryHandler implements QueryHandler<GetUserInvitationsQuery, ResponseEntity<List<InvitationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserInvitationsQueryHandler.class);

    private final InvitationEntityRepository invitationRepository;
    private final InvitationMapper invitationMapper;
    private final UserUtils utils;

    @Value("${igrp.app-center.url:}")
    private String appCenterUrl = "";

    public GetUserInvitationsQueryHandler(InvitationEntityRepository invitationRepository, InvitationMapper invitationMapper, UserUtils utils) {
        this.invitationRepository = invitationRepository;
        this.invitationMapper = invitationMapper;
        this.utils = utils;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<InvitationDTO>> handle(GetUserInvitationsQuery query) {

        LOGGER.info("Retrieving user invitations with email filter: {}", query.getEmail());

        var invitations = invitationRepository.findAllByStatusNotOrderByLastModifiedDateDesc(InvitationStatus.ACCEPTED)
                .stream()
                .filter(it -> query.getEmail() == null || it.getEmail().equals(query.getEmail()))
                .map(it -> {
                    var url = utils.constructInvitationUrl(appCenterUrl, it.getToken());
                    return invitationMapper.toDtoWithUrl(it, url);
                })
                .toList();

        LOGGER.info("Successfully retrieved {} user invitations", invitations.size());

        return ResponseEntity.ok(invitations);

    }

}