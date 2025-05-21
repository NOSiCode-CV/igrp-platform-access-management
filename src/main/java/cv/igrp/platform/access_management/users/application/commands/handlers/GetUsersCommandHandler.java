package cv.igrp.platform.access_management.users.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;

import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.users.application.commands.commands.GetUsersCommand;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import jakarta.persistence.criteria.JoinType;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.domain.models.IGRPUser;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.IGRPUserRepository;

@Service
public class GetUsersCommandHandler implements CommandHandler<GetUsersCommand, ResponseEntity<List<IGRPUserDTO>>> {

    private static final Logger logger = LoggerFactory.getLogger(GetUsersCommandHandler.class);
    private final IGRPUserRepository userRepository;
    private final IGRPUserMapper userMapper;

    public GetUsersCommandHandler(IGRPUserRepository userRepository, IGRPUserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<IGRPUserDTO>> handle(GetUsersCommand command) {
        logger.info("Handling GetUsersCommand: applicationId={}, departmentId={}, name={}, username={}, email={}",
                command.getApplicationId(), command.getDepartmentId(), command.getName(),
                command.getUsername(), command.getEmail());

        Specification<IGRPUser> spec = buildSpecification(command);
        List<IGRPUserDTO> users = userRepository.findAll(spec)
                .stream()
                .map(userMapper::toDto)
                .toList();

        return ResponseEntity.ok(users);
    }

    private Specification<IGRPUser> buildSpecification(GetUsersCommand command) {
        Specification<IGRPUser> spec = Specification.where(null);

        if (command.getApplicationId() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                Join<Object, Object> applicationJoin = departmentJoin.join("applicationId", JoinType.INNER);
                return cb.equal(applicationJoin.get("id"), command.getApplicationId());
            });
        }

        if (command.getDepartmentId() != null) {
            spec = spec.and((root, q, cb) -> {
                Join<Object, Object> roleJoin = root.join("roles", JoinType.INNER);
                Join<Object, Object> departmentJoin = roleJoin.join("department", JoinType.INNER);
                return cb.equal(departmentJoin.get("id"), command.getDepartmentId());
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
