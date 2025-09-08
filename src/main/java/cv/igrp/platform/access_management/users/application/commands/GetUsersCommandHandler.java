package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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

   public GetUsersCommandHandler(IGRPUserEntityRepository userRepository, IGRPUserMapper userMapper) {
      this.userRepository = userRepository;
      this.userMapper = userMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersCommand command) {
      logger.info("Handling GetUsersCommand: applicationCode={}, departmentCode={}, name={}, username={}, email={}",
              command.getApplicationCode(), command.getDepartmentCode(), command.getName(),
              command.getUsername(), command.getEmail());

      Specification<IGRPUserEntity> spec = buildSpecification(command);
      List<IGRPUserDTO> users = userRepository.findAll(spec)
              .stream()
              .map(userMapper::toDto)
              .toList();

      return ResponseEntity.ok(users);
   }

   private Specification<IGRPUserEntity> buildSpecification(GetUsersCommand command) {
      Specification<IGRPUserEntity> spec = Specification.anyOf();

      if (command.getApplicationCode() != null) {
         spec = spec.and((root, q, cb) -> {
            Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
            Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
            Join<Object, Object> applicationJoin = departmentJoin.join("applicationId", JoinType.INNER);
            return cb.equal(applicationJoin.get("code"), command.getApplicationCode());
         });
      }

      if (command.getDepartmentCode() != null) {
         spec = spec.and((root, q, cb) -> {
            Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
            Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
            return cb.equal(departmentJoin.get("code"), command.getDepartmentCode());
         });
      }

      if (command.getName() != null && !command.getName().isEmpty()) {
         spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), "%" + command.getName().toLowerCase() + "%"));
      }

      if (command.getUsername() != null && !command.getUsername().isEmpty()) {
         spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("username")), "%" + command.getUsername().toLowerCase() + "%"));
      }

      if (command.getEmail() != null && !command.getEmail().isEmpty()) {
         spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("email")), "%" + command.getEmail().toLowerCase() + "%"));
      }

      return spec;
   }

}