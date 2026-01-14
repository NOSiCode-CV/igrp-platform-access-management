package cv.igrp.platform.access_management.m2m.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.department.specs.DepartmentSpecificationBuilder;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

@Component
public class GetDepartmentForBusinessCommandHandler implements CommandHandler<GetDepartmentForBusinessCommand, ResponseEntity<List<DepartmentDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentForBusinessCommandHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final DepartmentSpecificationBuilder departmentSpecificationBuilder;
    private final DepartmentMapper departmentMapper;
    private final AuthenticationHelper authenticationHelper;

    public GetDepartmentForBusinessCommandHandler(DepartmentEntityRepository departmentRepository, DepartmentSpecificationBuilder departmentSpecificationBuilder, DepartmentMapper departmentMapper, AuthenticationHelper authenticationHelper) {
        this.departmentRepository = departmentRepository;
        this.departmentSpecificationBuilder = departmentSpecificationBuilder;
        this.departmentMapper = departmentMapper;
        this.authenticationHelper = authenticationHelper;
    }

    @IgrpCommandHandler
    public ResponseEntity<List<DepartmentDTO>> handle(GetDepartmentForBusinessCommand command) {

        LOGGER.info("Getting Users For Business [%s]".formatted(authenticationHelper.getSub()));

        var specification = departmentSpecificationBuilder.buildSpecification(command);

        var departments = departmentRepository.findAll(specification)
                .stream()
                .map(departmentMapper::toDto)
                .toList();

        LOGGER.info("Sending {} departments to business [{}]", departments.size(), authenticationHelper.getSub());

        return ResponseEntity.ok(departments);

    }

}