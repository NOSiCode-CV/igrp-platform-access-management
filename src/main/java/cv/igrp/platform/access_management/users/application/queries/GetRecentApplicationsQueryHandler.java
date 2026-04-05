package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.AccessHistoryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetRecentApplicationsQueryHandler implements QueryHandler<GetRecentApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetRecentApplicationsQueryHandler.class);

    private final AccessHistoryEntityRepository accessHistoryRepository;
    private final IGRPUserEntityRepository userEntityRepository;
    private final ApplicationMapper applicationMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetRecentApplicationsQueryHandler(AccessHistoryEntityRepository accessHistoryRepository, IGRPUserEntityRepository userEntityRepository, ApplicationMapper applicationMapper, AuthenticationHelper authenticationHelper) {
        this.accessHistoryRepository = accessHistoryRepository;
        this.userEntityRepository = userEntityRepository;
        this.applicationMapper = applicationMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    @Transactional
    public ResponseEntity<List<ApplicationDTO>> handle(GetRecentApplicationsQuery query) {

        var currentUserSub = authenticationHelper.getSub();

        LOGGER.info("Getting recent applications for user: {}", currentUserSub);

        var user = userEntityRepository.findByExternalIdWithRolesAndPermissions(currentUserSub).orElseThrow(
                () -> IgrpResponseStatusException.of(HttpStatus.UNAUTHORIZED, "User with external ID " + currentUserSub + " not found")
        );

        var accessHistory = accessHistoryRepository.findByUserIdOrderByLastAccessDesc(Integer.valueOf(user.getId()));

        var applicationDTOs = accessHistory.stream().map(it -> {
            var applicationDTO = applicationMapper.toDto(it.getApplication());
            applicationDTO.setLastAccess(it.getLastAccess());
            return applicationDTO;
        }).toList();

        if(query.getMax() != null && query.getMax() > 0) {
            applicationDTOs = applicationDTOs.subList(0, query.getMax());
        }

        if(query.getApplicationCode() != null && !query.getApplicationCode().isEmpty()) {
            applicationDTOs = applicationDTOs.stream().filter(it -> it.getCode().equals(query.getApplicationCode())).toList();
        }

        if(query.getApplicationName() != null && !query.getApplicationName().isEmpty()) {
            applicationDTOs = applicationDTOs.stream().filter(it -> it.getName().toLowerCase().contains(query.getApplicationName().toLowerCase())).toList();
        }

        return ResponseEntity.ok(applicationDTOs);

    }

}