package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Component
public class GetCurrentUserApplicationsQueryHandler implements QueryHandler<GetCurrentUserApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserApplicationsQueryHandler.class);

    private final ApplicationEntityRepository applicationRepository;
    private final IGRPUserEntityRepository userRepository;
    private final ApplicationMapper applicationMapper;
    private final AuthenticationHelper authenticationHelper;

    @Value("${igrp.superadmin.user-external-id}")
    public String SUPER_ADMIN_EXTERNAL_ID = "";

    public GetCurrentUserApplicationsQueryHandler(
            ApplicationEntityRepository applicationRepository,
            IGRPUserEntityRepository userRepository,
            ApplicationMapper applicationMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.applicationMapper = applicationMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<ApplicationDTO>> handle(GetCurrentUserApplicationsQuery query) {

        var user = userRepository.findByExternalId(authenticationHelper.getSub()).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with external ID: " + authenticationHelper.getSub() + " not found in database."
                )
        );

        LOGGER.info("Getting applications for user: {}", user.getEmail());

        List<ApplicationDTO> applications = user.getExternalId().equals(SUPER_ADMIN_EXTERNAL_ID) ?
                applicationRepository.findAllAndStatusActive()
                        .stream()
                        .filter(it -> query.getApplicationCode() == null || it.getCode().contains(query.getApplicationCode()))
                        .filter(it -> query.getApplicationName() == null || it.getName().toLowerCase().contains(query.getApplicationName().toLowerCase()))
                        .map(applicationMapper::toDto)
                        .toList()
                : applicationRepository.findByUserIdAndStatusActive(user)
                .stream()
                .filter(it -> query.getApplicationCode() == null || it.getCode().contains(query.getApplicationCode()))
                .filter(it -> query.getApplicationName() == null || it.getName().toLowerCase().contains(query.getApplicationName().toLowerCase()))
                .map(applicationMapper::toDto)
                .toList();

        return ResponseEntity.ok(applications);

    }

}