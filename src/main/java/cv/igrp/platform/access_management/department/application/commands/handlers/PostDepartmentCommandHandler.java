package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.PostDepartmentCommand;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class PostDepartmentCommandHandler implements CommandHandler<PostDepartmentCommand, ResponseEntity<DepartmentDTO>> {

    private final DepartmentRepository departmentRepository;
    private final ApplicationRepository applicationRepository;
    private final DepartmentMapper departmentMapper;

    public PostDepartmentCommandHandler(DepartmentRepository departmentRepository, ApplicationRepository applicationRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.applicationRepository = applicationRepository;
        this.departmentMapper = departmentMapper;
    }

    @IgrpCommandHandler
    public ResponseEntity<DepartmentDTO> handle(PostDepartmentCommand command) {
        Department department = departmentMapper.toEntity(command.getDepartmentdto());
        department.setApplicationId(applicationRepository.findById(command.getDepartmentdto().getApplication_id()).orElseThrow(() -> new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.BAD_REQUEST, "Invalid application ID", null))));

        Department parent = departmentRepository.findById(command.getDepartmentdto().getParent_id()).orElseThrow(() -> new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.BAD_REQUEST, "Invalid department ID", null)));

        department.setParentId(parent);

        Department saved = departmentRepository.save(department);
        DepartmentDTO result = departmentMapper.toDto(saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}