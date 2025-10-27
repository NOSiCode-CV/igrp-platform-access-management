package cv.igrp.platform.access_management.authorization.application.commands.handler;

import cv.igrp.framework.auth.core.authorization.model.PermissionCheckRequest;
import cv.igrp.framework.auth.core.authorization.model.PermissionCheckResponse;
import cv.igrp.framework.auth.core.authorization.service.AuthorizationCore;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import org.springframework.stereotype.Service;

import java.util.Arrays;
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

        try {
            PermissionCheckResponse permissionCheckResponse = authorizationCore.check(request).get();
            var responseDto = new PermissionCheckResponseDTO();
            responseDto.setAllowed(permissionCheckResponse.isAllowed());
            responseDto.setViaRoles(permissionCheckResponse.getViaRoles());
            responseDto.setReason(permissionCheckResponse.getReason());
            responseDto.setCacheHit(permissionCheckResponse.isCacheHit());
            responseDto.setResolutionTimeMs(permissionCheckResponse.getResolutionTimeMs());
            return responseDto;
        } catch (InterruptedException | ExecutionException e) {
            System.err.println(e.getMessage());
            System.err.println(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }
}
