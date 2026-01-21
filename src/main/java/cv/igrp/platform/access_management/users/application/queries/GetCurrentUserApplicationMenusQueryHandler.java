package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
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

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetCurrentUserApplicationMenusQueryHandler implements QueryHandler<GetCurrentUserApplicationMenusQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetCurrentUserApplicationMenusQueryHandler.class);

    private final MenuEntryEntityRepository menuEntryRepository;
    private final IGRPUserEntityRepository userRepository;
    private final ApplicationEntityRepository applicationRepository;
    private final MenuEntryMapper menuEntryMapper;
    private final AuthenticationHelper authenticationHelper;

    @Value("${igrp.superadmin.user-external-id}")
    public String SUPER_ADMIN_EXTERNAL_ID = "";

    public GetCurrentUserApplicationMenusQueryHandler(
            MenuEntryEntityRepository menuEntryRepository,
            IGRPUserEntityRepository userRepository,
            ApplicationEntityRepository applicationRepository,
            MenuEntryMapper menuEntryMapper,
            AuthenticationHelper authenticationHelper
    ) {
        this.menuEntryRepository = menuEntryRepository;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.menuEntryMapper = menuEntryMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<MenuEntryDTO>> handle(GetCurrentUserApplicationMenusQuery query) {

        var user = userRepository.findByExternalId(authenticationHelper.getSub()).orElseThrow(
                () -> IgrpResponseStatusException.of(
                        HttpStatus.UNAUTHORIZED,
                        "User not found",
                        "User with external ID: " + authenticationHelper.getSub() + " not found in database."
                )
        );

        LOGGER.info("Getting menus for user: {}", user.getExternalId());

        var application = applicationRepository.findByCodeAndStatusNotDeleted(query.getApplicationCode());

        List<MenuEntryDTO> menuEntries = user.getExternalId().equals(SUPER_ADMIN_EXTERNAL_ID) ?
                menuEntryRepository.findByApplicationAndStatusAndMenuName(application.getId(), List.of(Status.ACTIVE.getCode()), query.getMenuCode())
                        .stream()
                        .map(menuEntryMapper::toDTO)
                        .toList()
                : menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(Integer.valueOf(user.getId()), application.getId(), query.getMenuCode())
                .stream()
                .map(menuEntryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(menuEntries);

    }

}