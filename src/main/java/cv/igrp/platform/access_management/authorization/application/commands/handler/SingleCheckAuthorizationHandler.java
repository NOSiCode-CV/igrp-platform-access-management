package cv.igrp.platform.access_management.authorization.application.commands.handler;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutionException;

@Service
public class SingleCheckAuthorizationHandler {

    private final AuthorizationCore authorizationCore;

    public SingleCheckAuthorizationHandler(AuthorizationCore authorizationCore) {
        this.authorizationCore = authorizationCore;
    }

    public PermissionCheckResponseDTO checkAuthorization(String username, String action, String resource) {
        PermissionCheckRequest request = new PermissionCheckRequest();
        request.setAction(action);
        request.setResource(resource);
        request.setSubject(username);

        var result = this.authorizationCore.check(request);

        try {
            var permissionCheckResponse = result.get();

            var responseDto = new PermissionCheckResponseDTO();
            responseDto.setAllowed(permissionCheckResponse.isAllowed());
            responseDto.setCache_hit(permissionCheckResponse.isCacheHit());
            responseDto.setVia_roles(permissionCheckResponse.getViaRoles());
            responseDto.setResolution_time_ms(permissionCheckResponse.getResolutionTimeMs());

            return responseDto;
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
