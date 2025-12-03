package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.users.specs.UserSpecificationBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Component
public class GetUsersForBusinessCommandHandler implements CommandHandler<GetUsersForBusinessCommand, ResponseEntity<List<IGRPUserDTO>>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(GetUsersForBusinessCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final AuthenticationHelper authenticationHelper;
   private final UserSpecificationBuilder userSpecificationBuilder;

   public GetUsersForBusinessCommandHandler(IGRPUserEntityRepository userRepository, IGRPUserMapper userMapper, AuthenticationHelper authenticationHelper, UserSpecificationBuilder userSpecificationBuilder) {
      this.userRepository = userRepository;
      this.userMapper = userMapper;
      this.authenticationHelper = authenticationHelper;
      this.userSpecificationBuilder = userSpecificationBuilder;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersForBusinessCommand command) {

       LOGGER.info("Getting Users For Business [%s]".formatted(authenticationHelper.getSub()));

       if (command.getApplicationCode() == null
               && command.getDepartmentCode() == null
               && command.getRoleCode() == null
               && command.getPermissionName() == null) {
           throw IgrpResponseStatusException.badRequest("At least one filter must be provided: applicationCode, departmentCode, roleCode or permissionName");
       }

       var specification = userSpecificationBuilder.buildSpecification(command);

      List<IGRPUserDTO> users = userRepository.findAll(specification)
              .stream()
              .map(userMapper::toDto)
              .toList();

      LOGGER.info("Sending {} users for business [{}]", users.size(), authenticationHelper.getSub());

      return ResponseEntity.ok(users);

   }

}