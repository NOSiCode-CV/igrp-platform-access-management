package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import cv.igrp.platform.access_management.users.specs.UserSpecificationBuilder;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;

@Component
public class GetUsersCommandHandler implements CommandHandler<GetUsersCommand, ResponseEntity<List<IGRPUserDTO>>> {

   private static final Logger logger = LoggerFactory.getLogger(GetUsersCommandHandler.class);

   private final IGRPUserEntityRepository userRepository;
   private final IGRPUserMapper userMapper;
   private final UserSpecificationBuilder specification;

   public GetUsersCommandHandler(IGRPUserEntityRepository userRepository, IGRPUserMapper userMapper, UserSpecificationBuilder specification) {
      this.userRepository = userRepository;
      this.userMapper = userMapper;
      this.specification = specification;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersCommand command) {
      logger.info("Handling GetUsersCommand: applicationCode={}, departmentCode={}, name={}, id={}, email={}",
              command.getApplicationCode(), command.getDepartmentCode(), command.getName(),
              command.getId(), command.getEmail());

      Specification<IGRPUserEntity> spec = specification.buildSpecification(command, new ScopeContext());
      List<IGRPUserDTO> users = userRepository.findAll(spec)
              .stream()
              .map(userMapper::toDto)
              .toList();

      return ResponseEntity.ok(users);
   }

}