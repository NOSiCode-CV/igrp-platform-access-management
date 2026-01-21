package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Component
public class GetUserApplicationsQueryHandler implements QueryHandler<GetUserApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetUserApplicationsQueryHandler.class);

    private final ApplicationEntityRepository applicationRepository;
    private final IGRPUserEntityRepository userRepository;
    private final ApplicationMapper applicationMapper;

    public GetUserApplicationsQueryHandler(ApplicationEntityRepository applicationRepository, IGRPUserEntityRepository userRepository, ApplicationMapper applicationMapper) {
        this.applicationRepository = applicationRepository;
        this.userRepository = userRepository;
        this.applicationMapper = applicationMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<ApplicationDTO>> handle(GetUserApplicationsQuery query) {

        var user = userRepository.findById(query.getId()).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with ID: " + query.getId() + " not found in database."
                )
        );

        LOGGER.info("Getting applications for user: {}", user.getEmail());

        List<ApplicationDTO> applications = applicationRepository.findByUserAndActiveFiltered(Integer.valueOf(user.getId()), query.getApplicationCode(), query.getApplicationName())
                .stream()
                .map(applicationMapper::toDto)
                .toList();

        return ResponseEntity.ok(applications);

    }

}