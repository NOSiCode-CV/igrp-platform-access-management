package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

@Component
public class GetAppMenusQueryHandler implements QueryHandler<GetAppMenusQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetAppMenusQueryHandler.class);

    private final AuthenticationHelper authenticationHelper;
    private final IGRPUserEntityRepository igrpUserRepository;
    private final ApplicationEntityRepository applicationRepository;
    private final MenuEntryEntityRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;

    public GetAppMenusQueryHandler(AuthenticationHelper authenticationHelper,
                                   IGRPUserEntityRepository igrpUserRepository,
                                   ApplicationEntityRepository applicationRepository,
                                   MenuEntryEntityRepository menuEntryRepository,
                                   MenuEntryMapper menuEntryMapper
    ) {
        this.authenticationHelper = authenticationHelper;
        this.igrpUserRepository = igrpUserRepository;
        this.applicationRepository = applicationRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<MenuEntryDTO>> handle(GetAppMenusQuery query) {
        String externalId = authenticationHelper.getPreferredUsername();
        LOGGER.info("Fetching current user with sub: {}", externalId);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findByExternalId(externalId);
        if (optionalUser.isEmpty()) {
            LOGGER.warn("No user found with sub: {}", externalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        IGRPUserEntity user = optionalUser.get();

        // Step 1: Get application by appCode
        Optional<ApplicationEntity> optionalApp = applicationRepository.findByCodeAndStatusNot(query.getAppCode(), Status.DELETED);
        if (optionalApp.isEmpty()) {
            LOGGER.warn("No application found with code: {}", query.getAppCode());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        ApplicationEntity app = optionalApp.get();

        // Step 2: Get user's role IDs from roles
        Set<Integer> userRoleIds = user.getRoles().stream()
                .filter(role -> Objects.nonNull(role) && !role.getStatus().equals(Status.DELETED))
                .map(RoleEntity::getId)
                .collect(Collectors.toSet());

        if (userRoleIds.isEmpty()) {
            LOGGER.warn("User {} has no roles assigned", externalId);
            return ResponseEntity.ok(Collections.emptyList());
        }

        // Step 3: Fetch all active menus for the app (via repository)
        List<MenuEntryEntity> allMenus = menuEntryRepository.findByApplicationIdAndTypeInAndStatusIn(app, List.of(MenuEntryType.MENU_PAGE, MenuEntryType.SYSTEM_PAGE, MenuEntryType.EXTERNAL_PAGE), List.of(Status.ACTIVE));

        // Step 4: Filter only menus user has role for (or no role required)
        List<MenuEntryDTO> accessibleMenus = allMenus.stream()
                .filter(menu -> {
                    Set<RoleEntity> menuRoles = menu.getRoles();
                    if(menuRoles == null || menuRoles.isEmpty()) {
                        return false;
                    }
                    return menuRoles.stream()
                                    .filter(role -> role.getStatus() == Status.ACTIVE)
                                    .map(RoleEntity::getId)
                                    .anyMatch(userRoleIds::contains);
                })
                .map(menuEntryMapper::toDTO)
                .collect(Collectors.toList());

        LOGGER.info("User {} has access to {} menus in application {}", externalId, accessibleMenus.size(), query.getAppCode());

        return ResponseEntity.ok(accessibleMenus);
    }

}