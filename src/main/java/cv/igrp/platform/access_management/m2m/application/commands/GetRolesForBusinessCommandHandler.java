package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.role.specs.RoleSpecificationBuilder;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;

@Component
public class GetRolesForBusinessCommandHandler implements CommandHandler<GetRolesForBusinessCommand, ResponseEntity<List<RoleDTO>>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(GetRolesForBusinessCommandHandler.class);

   private final RoleEntityRepository roleEntityRepository;
   private final RoleSpecificationBuilder roleSpecificationBuilder;
   private final RoleMapper roleMapper;
   private final AuthenticationHelper authenticationHelper;

   public GetRolesForBusinessCommandHandler(RoleEntityRepository roleEntityRepository, RoleSpecificationBuilder roleSpecificationBuilder, RoleMapper roleMapper, AuthenticationHelper authenticationHelper) {
      this.roleEntityRepository = roleEntityRepository;
      this.roleSpecificationBuilder = roleSpecificationBuilder;
      this.roleMapper = roleMapper;
      this.authenticationHelper = authenticationHelper;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<RoleDTO>> handle(GetRolesForBusinessCommand command) {

      LOGGER.info("Getting Roles for Business [{}]", authenticationHelper.getSub());

      var specification = roleSpecificationBuilder.buildSpecification(command);

      var roles = roleEntityRepository.findAll(specification)
               .stream()
               .map(roleMapper::mapToDto)
               .toList();

      LOGGER.info("Sending {} roles to business [{}]", roles.size(), authenticationHelper.getSub());

      return ResponseEntity.ok(roles);

   }

}