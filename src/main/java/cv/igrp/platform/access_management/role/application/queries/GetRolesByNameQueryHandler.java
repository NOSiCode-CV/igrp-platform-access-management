package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
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
    String rolename = query.getName();
    LOGGER.info("Fetching role with name={}", rolename);

     RoleEntity role = roleEntityRepository.findByName(rolename)
             .orElseThrow(() -> {
               LOGGER.warn("Role with name={} not found", rolename);
               return IgrpResponseStatusException.of(
                       HttpStatus.NOT_FOUND, "Invalide Role NAME", "Role not found with name: " + rolename);

             });
     RoleDTO dto = roleMapper.mapToDto(role);
     LOGGER.info("Successfully retrieved role name={} name={}", dto.getName(), dto.getName());
     return ResponseEntity.ok(dto);
  }

}