package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;

@Component
public class GetPermissionByNameQueryHandler implements QueryHandler<GetPermissionByNameQuery, ResponseEntity<PermissionDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetPermissionByNameQueryHandler.class);

  private final PermissionEntityRepository permissionEntityRepository;
  private final PermissionMapper permissionMapper;


  public GetPermissionByNameQueryHandler(PermissionEntityRepository permissionEntityRepository, PermissionMapper permissionMapper) {
    this.permissionEntityRepository = permissionEntityRepository;
    this.permissionMapper = permissionMapper;

  }

   @IgrpQueryHandler
  public ResponseEntity<PermissionDTO> handle(GetPermissionByNameQuery query) {
    String permissioname = query.getName();
    LOGGER.info("Fetching role with name={}", permissioname);

     PermissionEntity permission = permissionEntityRepository.findByNameAndStatusNot(permissioname, Status.DELETED)
             .orElseThrow(() -> {
               LOGGER.warn("permission with name={} not found", permissioname);
               return IgrpResponseStatusException.of(
                       HttpStatus.NOT_FOUND, "Invalide Permission Name", "Permission not found with name: " + permissioname);

             });
     PermissionDTO dto = permissionMapper.mapToDTO(permission);
     LOGGER.info("Succesfully retrieved permission name={} name={}",dto.getName(), dto.getName());
     return ResponseEntity.ok(dto);
  }


}