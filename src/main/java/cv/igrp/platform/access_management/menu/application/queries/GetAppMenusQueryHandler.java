package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
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
        String username = authenticationHelper.getPreferredUsername();
        LOGGER.info("Fetching current user with username: {}", username);

        Optional<IGRPUserEntity> optionalUser = igrpUserRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            LOGGER.warn("No user found with username: {}", username);
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

        // Step 2: Get user's permission IDs from roles
        Set<Integer> userPermissionIds = user.getRoles().stream()
                .filter(role -> Objects.nonNull(role) && !role.getStatus().equals(Status.DELETED))
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getId)
                .collect(Collectors.toSet());

        if (userPermissionIds.isEmpty()) {
            LOGGER.warn("User {} has no permissions assigned", username);
            return ResponseEntity.ok(Collections.emptyList());
        }

        // Step 3: Fetch all active menus for the app (via repository)
        List<MenuEntryEntity> allMenus = menuEntryRepository.findByApplicationIdAndStatus(app, Status.ACTIVE);

        // Step 4: Filter only menus user has permission for (or no permission required)
        List<MenuEntryDTO> accessibleMenus = allMenus.stream()
                .filter(menu -> {
                    Set<PermissionEntity> menuPermissions = menu.getPermissions();
                    if(menuPermissions == null || menuPermissions.isEmpty()) {
                        return false;
                    }
                    return menuPermissions.stream()
                                    .filter(permission -> permission.getStatus() == Status.ACTIVE)
                                    .map(PermissionEntity::getId)
                                    .anyMatch(userPermissionIds::contains);
                })
                .map(menuEntryMapper::toDTO)
                .collect(Collectors.toList());

        LOGGER.info("User {} has access to {} menus in application {}", username, accessibleMenus.size(), query.getAppCode());

        return ResponseEntity.ok(accessibleMenus);
    }

}