package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.framework.auth.generated.PermissionsRegistry;
import cv.igrp.platform.access_management.m2m.domain.service.PermissionSyncService;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Automatically synchronizes code-defined permissions with the Access Management API.
 */
@Component
public class AuthorizationSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationSyncService.class);

    private final PermissionSyncService syncService;

    @Value("${spring.application.name:}")
    private String applicationName;

    public AuthorizationSyncService(PermissionSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Runs once the application is ready, not at bean construction. The
     * permissions are filed under this service's own resource, which
     * {@code ConfigurationService} creates from a {@code CommandLineRunner}; on
     * a fresh database a {@code @PostConstruct} sync ran before that resource
     * existed, failed with "Resource not found", and left the first boot with
     * no permissions at all. {@link ApplicationReadyEvent} fires after every
     * runner and before readiness accepts traffic.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncAuthorization() {
        try {
            LOGGER.info("[Authorization Sync] Starting authorization synchronization with Access Management API...");

            List<PermissionDTO> permissions = Arrays.stream(PermissionsRegistry.Permission.values())
                    .map(p -> {
                        var perm = new PermissionDTO();
                        perm.setName(p.getCode());
                        perm.setDescription(p.getDescription());
                        perm.setStatus(p.enabled() ? Status.ACTIVE : Status.INACTIVE);
                        return perm;
                    })
                    .toList();

            LOGGER.info("[Authorization Sync] Synchronizing {} permissions for application '{}'", permissions.size(), applicationName);

            syncService.synchronizePermissions(permissions, true);

            LOGGER.info("[Permission Sync] Successfully synchronized {} permissions.", permissions.size());

        } catch (Exception ex) {
            LOGGER.error("[Permission Sync] Failed to synchronize authorization with Access Management API", ex);
        }
    }
}