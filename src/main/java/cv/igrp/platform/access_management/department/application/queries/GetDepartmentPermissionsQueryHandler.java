package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetDepartmentPermissionsQueryHandler implements QueryHandler<GetDepartmentPermissionsQuery, ResponseEntity<List<PermissionDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentPermissionsQueryHandler.class);

    private final PermissionEntityRepository permissionRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final PermissionMapper permissionMapper;

    public GetDepartmentPermissionsQueryHandler(PermissionEntityRepository permissionRepository, DepartmentEntityRepository departmentRepository, PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.departmentRepository = departmentRepository;
        this.permissionMapper = permissionMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<PermissionDTO>> handle(GetDepartmentPermissionsQuery query) {

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getCode());

        List<PermissionDTO> permissions = permissionRepository.findByDepartmentAndStatusNot(department, Status.DELETED)
                .stream()
                .filter(it -> query.getPermissionName() == null || it.getName().contains(query.getPermissionName()))
                .map(permissionMapper::mapToDTO)
                .toList();

        return ResponseEntity.ok(permissions);

    }

}