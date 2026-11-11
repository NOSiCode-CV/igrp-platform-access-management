package cv.igrp.platform.access_management.permission_fetch.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.permission_fetch.application.dto.PermissionSetDTO;
import cv.igrp.platform.access_management.permission_fetch.domain.service.PermissionFetchService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class FetchPermissionsQueryHandler
        implements QueryHandler<FetchPermissionsQuery, ResponseEntity<PermissionSetDTO>> {

    private final PermissionFetchService permissions;

    public FetchPermissionsQueryHandler(PermissionFetchService permissions) {
        this.permissions = permissions;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<PermissionSetDTO> handle(FetchPermissionsQuery query) {
        return ResponseEntity.ok(permissions.fetch(query.applicationCode()));
    }
}
