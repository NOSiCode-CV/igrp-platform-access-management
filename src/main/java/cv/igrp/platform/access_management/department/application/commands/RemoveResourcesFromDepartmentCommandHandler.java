package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RemoveResourcesFromDepartmentCommandHandler implements CommandHandler<RemoveResourcesFromDepartmentCommand, ResponseEntity<String>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveResourcesFromDepartmentCommandHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final ResourceEntityRepository resourceRepository;


    public RemoveResourcesFromDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, ResourceEntityRepository resourceRepository) {
        this.departmentRepository = departmentRepository;
        this.resourceRepository = resourceRepository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<String> handle(RemoveResourcesFromDepartmentCommand command) {

        var department = departmentRepository.findByCodeAndStatusNotDeleted(command.getDepartmentCode());

        for (var resourceName : command.getRemoveResourcesFromDepartmentRequest()) {

            var resource = resourceRepository.findByNameNotDeleted(resourceName);

            resource.getDepartments().remove(department);

            resourceRepository.save(resource);

        }

        return ResponseEntity.noContent().build();

    }

}