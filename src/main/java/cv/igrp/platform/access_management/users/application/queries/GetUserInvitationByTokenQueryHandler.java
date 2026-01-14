package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;

@Component
public class GetUserInvitationByTokenQueryHandler implements QueryHandler<GetUserInvitationByTokenQuery, ResponseEntity<InvitationDTO>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserInvitationByTokenQueryHandler.class);

    private final InvitationEntityRepository invitationEntityRepository;
    private final InvitationMapper invitationMapper;
    private final UserUtils userUtils;

    @Value("${igrp.app-center.url:}")
    private String appCenterUrl = "";

    public GetUserInvitationByTokenQueryHandler(InvitationEntityRepository invitationEntityRepository, InvitationMapper invitationMapper, UserUtils userUtils) {
        this.invitationEntityRepository = invitationEntityRepository;
        this.invitationMapper = invitationMapper;
        this.userUtils = userUtils;
    }

    @IgrpQueryHandler
    public ResponseEntity<InvitationDTO> handle(GetUserInvitationByTokenQuery query) {
        final var invitationEntity = invitationEntityRepository.findByTokenOrThrow(query.getToken());
        final var url = userUtils.constructInvitationUrl(appCenterUrl, invitationEntity.getToken());
        final var invitationDTO = invitationMapper.toDtoWithUrl(invitationEntity, url);
        return ResponseEntity.ok(invitationDTO);
    }

}