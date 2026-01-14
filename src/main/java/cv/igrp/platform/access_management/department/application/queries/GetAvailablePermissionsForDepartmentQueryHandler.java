package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_PERMISSION;

@Component
public class GetAvailablePermissionsForDepartmentQueryHandler implements QueryHandler<GetAvailablePermissionsForDepartmentQuery, ResponseEntity<List<PermissionDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailablePermissionsForDepartmentQueryHandler.class);

  private final PermissionMapper permissionMapper;
  private final PermissionEntityRepository  permissionEntityRepository;
  private final DepartmentEntityRepository departmentEntityRepository;
  
  public GetAvailablePermissionsForDepartmentQueryHandler(
          PermissionMapper permissionMapper,
          PermissionEntityRepository permissionEntityRepository,
          DepartmentEntityRepository departmentEntityRepository
  ) {
    this.permissionMapper = permissionMapper;
    this.permissionEntityRepository = permissionEntityRepository;
    this.departmentEntityRepository = departmentEntityRepository;
  }

   @IgrpQueryHandler
  public ResponseEntity<List<PermissionDTO>> handle(GetAvailablePermissionsForDepartmentQuery query) {

     LOGGER.info("Getting Available Permissions for department: {}", query.getDepartmentCode());

     // Verify if the department exists
     departmentEntityRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

     List<PermissionDTO> availablePermissions = permissionEntityRepository.findAvailablePermissionsForDepartment(query.getDepartmentCode(), IGRP_PERMISSION)
             .stream()
             .filter(it -> query.getResourceName() == null || it.getResources().stream().map(ResourceEntity::getName).toList().contains(query.getResourceName()))
             .map(permissionMapper::mapToDTO)
             .toList();

     LOGGER.info("Found {} available permissions for department: {}", availablePermissions.size(), query.getDepartmentCode());

     return ResponseEntity.ok(availablePermissions);
    
  }

}