package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.generated.PermissionsRegistry;
import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckRequestDTO;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service("igrpAuthorization")
@SuppressWarnings("unused")
public class IgrpAuthorizationService {

    private final SingleCheckAuthorizationHandler authorizationHandler;
    private final AuthenticationHelper authHelper;

    public IgrpAuthorizationService(SingleCheckAuthorizationHandler authorizationHandler, AuthenticationHelper authHelper) {
        this.authorizationHandler = authorizationHandler;
        this.authHelper = authHelper;
    }

    /**
     * Checks if the current user has a specific permission.
     *
     * @param action the permission enum (e.g. "Permission.FINANCE_SALARY_VIEW")
     * @return true if allowed, false otherwise
     */
    public boolean checkPermission(PermissionsRegistry.Permission action) {
        try {

            var username = authHelper.getSub();

            return authorizationHandler.checkAuthorization(
                    username, action.getCode(), null
            ).isAllowed();
        } catch (Exception e) {
            throw new RuntimeException("Error checking permission: " + action, e);
        }
    }

    /**
     * Checks if the user has ALL the given permissions.
     *
     * @param actions list or varargs of permission enums
     * @return true only if ALL are allowed
     */
    public boolean checkAllPermissions(PermissionsRegistry.Permission... actions) {
        if (actions == null || actions.length == 0) {
            return false;
        }

        try {

            List<PermissionCheckRequestDTO> requests = new ArrayList<>();
            Arrays.stream(actions).forEach(a -> requests.add(new PermissionCheckRequestDTO(null, a.getCode())));

            String username = authHelper.getSub();

            final List<PermissionCheckResponseDTO> responses = new ArrayList<>();

            for (var dto : requests) {
                var action = dto.getAction();
                var resource = dto.getResource();

                responses.add(
                        this.authorizationHandler.checkAuthorization(username,action,resource)
                );
            }

            // Return true only if all are allowed
            return responses.stream().allMatch(PermissionCheckResponseDTO::isAllowed);

        } catch (Exception e) {
            throw new RuntimeException("Error checking all permissions: " + Arrays.toString(actions), e);
        }
    }

    /**
     * Checks if the user has ANY of the given permissions.
     *
     * @param actions list or varargs of permission enums
     * @return true if at least one is allowed
     */
    public boolean checkAnyPermission(PermissionsRegistry.Permission... actions) {
        if (actions == null || actions.length == 0) {
            return false;
        }

        try {
            List<PermissionCheckRequestDTO> requests = new ArrayList<>();
            Arrays.stream(actions).forEach(a -> requests.add(new PermissionCheckRequestDTO(null, a.getCode())));

            String username = authHelper.getSub();

            final List<PermissionCheckResponseDTO> responses = new ArrayList<>();

            for (var dto : requests) {
                var action = dto.getAction();
                var resource = dto.getResource();

                responses.add(
                        this.authorizationHandler.checkAuthorization(username,action,resource)
                );
            }

            // Return true only if any is allowed
            return responses.stream().anyMatch(PermissionCheckResponseDTO::isAllowed);

        } catch (Exception e) {
            throw new RuntimeException("Error checking any permissions: " + Arrays.toString(actions), e);
        }
    }
}