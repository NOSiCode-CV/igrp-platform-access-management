package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Component
public class GetUserDepartmentRolesQueryHandler implements QueryHandler<GetUserDepartmentRolesQuery, ResponseEntity<List<RoleDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetUserDepartmentRolesQueryHandler.class);

  private final RoleEntityRepository roleRepository;
  private final IGRPUserEntityRepository userRepository;
  private final DepartmentEntityRepository departmentRepository;
  private final RoleMapper roleMapper;

  public GetUserDepartmentRolesQueryHandler(RoleEntityRepository roleRepository, IGRPUserEntityRepository userRepository, DepartmentEntityRepository departmentRepository, RoleMapper roleMapper) {
    this.roleRepository = roleRepository;
    this.userRepository = userRepository;
    this.departmentRepository = departmentRepository;
    this.roleMapper = roleMapper;
  }

   @IgrpQueryHandler
  public ResponseEntity<List<RoleDTO>> handle(GetUserDepartmentRolesQuery query) {

     var user = userRepository.findById(query.getId()).orElseThrow(
             () -> IgrpResponseStatusException.of(
                     HttpStatus.UNAUTHORIZED,
                     "User not found",
                     "User with ID: " + query.getId() + " not found in database."
             )
     );

     LOGGER.info("Getting roles for user: {}", user.getExternalId());

     var department = departmentRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

     List<RoleDTO> roles = roleRepository.findByDepartmentIdAndUserIdAndStatusNotDeleted(user, department)
             .stream()
             .map(roleMapper::mapToDto)
             .toList();

     return ResponseEntity.ok(roles);

  }

}