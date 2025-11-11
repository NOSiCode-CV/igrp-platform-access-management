package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Component
public class GetRolesByNameQueryHandler implements QueryHandler<GetRolesByNameQuery, ResponseEntity<RoleDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetRolesByNameQueryHandler.class);

  private final RoleEntityRepository roleEntityRepository;
  private final RoleMapper roleMapper;


  public GetRolesByNameQueryHandler(RoleEntityRepository roleEntityRepository, RoleMapper roleMapper) {
    this.roleEntityRepository = roleEntityRepository;
    this.roleMapper = roleMapper;

  }

   @IgrpQueryHandler
  public ResponseEntity<RoleDTO> handle(GetRolesByNameQuery query) {
    String roleCode = query.getCode();
    LOGGER.info("Fetching role with code={}", roleCode);

     RoleEntity role = roleEntityRepository.findByCodeAndStatusNot(roleCode, Status.DELETED)
             .orElseThrow(() -> {
               LOGGER.warn("Role with code={} not found", roleCode);
               return IgrpResponseStatusException.of(
                       HttpStatus.NOT_FOUND, "Invalid Role code", "Role not found with code: " + roleCode);

             });
     RoleDTO dto = roleMapper.mapToDto(role);
     LOGGER.info("Successfully retrieved role code={}", dto.getCode());
     return ResponseEntity.ok(dto);
  }

}