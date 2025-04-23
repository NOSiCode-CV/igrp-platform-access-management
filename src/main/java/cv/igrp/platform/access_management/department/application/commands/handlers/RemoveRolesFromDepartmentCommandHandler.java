package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.department.application.commands.commands.RemoveRolesFromDepartmentCommand;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RemoveRolesFromDepartmentCommandHandler implements CommandHandler<RemoveRolesFromDepartmentCommand, ResponseEntity<List<RoleDTO>>> {
    private static final Logger logger = LoggerFactory.getLogger(RemoveRolesFromDepartmentCommandHandler.class);
    private final DepartmentRepository departmentRepository;
    private final RoleMapper roleMapper;

    public RemoveRolesFromDepartmentCommandHandler(DepartmentRepository departmentRepository, RoleMapper roleMapper) {
        this.departmentRepository = departmentRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(RemoveRolesFromDepartmentCommand command) {
        logger.info("Handling RemoveRolesFromDepartmentCommand for departmentId={} with {} roleIds", command.getDepartmentId(), command.getRoleIds().size());
        Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getDepartmentId()));

        List<Integer> roleIdsToRemove = command.getRoleIds();

        // Remover roles cujo ID está presente na lista
        department.getRoles().removeIf(role -> roleIdsToRemove.contains(role.getId()));

        Department updated = departmentRepository.save(department);

        List<RoleDTO> result = updated.getRoles().stream()
                .map(roleMapper::mapToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}