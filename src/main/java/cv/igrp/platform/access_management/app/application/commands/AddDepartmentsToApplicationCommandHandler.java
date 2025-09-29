package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class AddDepartmentsToApplicationCommandHandler implements CommandHandler<AddDepartmentsToApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final DepartmentEntityRepository departmentRepository;

    public AddDepartmentsToApplicationCommandHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository) {
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(AddDepartmentsToApplicationCommand command) {

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());

        for (var departmentCode : command.getCodelistrequestdto().getCodes()) {

            var department = departmentRepository.findByCodeAndStatusNotDeleted(departmentCode);

            application.getDepartments().add(department);
        }

        applicationRepository.save(application);

        return ResponseEntity.ok().build();
    }
}