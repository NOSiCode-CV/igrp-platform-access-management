package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.department.application.commands.commands.AddRolesToDepartmentCommand;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AddRolesToDepartmentCommandHandler implements CommandHandler<AddRolesToDepartmentCommand, ResponseEntity<List<RoleDTO>>> {

    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    //private final RoleMapper roleMapper;

    public AddRolesToDepartmentCommandHandler(DepartmentRepository departmentRepository, RoleRepository roleRepository/*, RoleMapper roleMapper*/) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        //this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<RoleDTO>> handle(AddRolesToDepartmentCommand command) {
        /*Department department = departmentRepository.findById(command.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + command.getDepartmentId()));

        List<Role> rolesToAdd = roleRepository.findAllById(
            command.getRoles().stream().map(RoleDTO::getId).collect(Collectors.toList())
        );

        department.getRoles().addAll(rolesToAdd);
        departmentRepository.save(department);

        List<RoleDTO> result = department.getRoles().stream().map(roleMapper::toDto).collect(Collectors.toList());*/
        return ResponseEntity.ok(new ArrayList<>());
    }
}
