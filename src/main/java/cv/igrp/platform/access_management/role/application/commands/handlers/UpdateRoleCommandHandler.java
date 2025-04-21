package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.role.application.commands.commands.UpdateRoleCommand;
import org.springframework.transaction.annotation.Transactional;


@Service
public class UpdateRoleCommandHandler implements CommandHandler<UpdateRoleCommand, ResponseEntity<RoleDTO>> {

    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleMapper roleMapper;

    public UpdateRoleCommandHandler(RoleRepository roleRepository, DepartmentRepository departmentRepository, RoleMapper roleMapper) {

        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.roleMapper = roleMapper;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<RoleDTO> handle(UpdateRoleCommand command) {
        RoleDTO newData = command.getRoledto();
        Department department = null;
        Role parentRole = null;
        Role roleToUpdate = roleRepository.findByIdAndStatusNot(command.getId(), Status.DELETED)
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Role", "Role with id: " + command.getId() + " not found.")
                ));
        if (newData.getDepartmentId() != null) {
            department = departmentRepository.findById(newData.getDepartmentId())
                    .orElseThrow(() -> new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Role", "Department with id: " + newData.getDepartmentId() + " not found.")
                    ));
        }
        if (newData.getParentId() != null) {
            parentRole = roleRepository.findById(newData.getParentId())
                    .orElseThrow(() -> new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Update Role", "Parent Role with id: " + newData.getParentId() + " not found.")
                    ));
        }
        roleToUpdate.setName(newData.getName());
        roleToUpdate.setDescription(newData.getDescription());
        roleToUpdate.setDepartment(department);
        roleToUpdate.setParent(parentRole);
        roleToUpdate.setStatus(newData.getStatus());
        Role updatedRole = roleRepository.save(roleToUpdate);

        return new ResponseEntity<>(roleMapper.mapToDto(updatedRole), HttpStatus.OK);
    }

}