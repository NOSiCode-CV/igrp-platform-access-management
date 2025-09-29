package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class RemoveDepartmentFromApplicationCommandHandler implements CommandHandler<RemoveDepartmentFromApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final DepartmentEntityRepository departmentRepository;

    public RemoveDepartmentFromApplicationCommandHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository) {
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(RemoveDepartmentFromApplicationCommand command) {

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());

        for (var departmentCode : command.getCodelistrequestdto().getCodes()) {

            var department = departmentRepository.findByCodeAndStatusNotDeleted(departmentCode);

            department.getApplications().remove(application);

            departmentRepository.save(department);

        }

        applicationRepository.save(application);

        return ResponseEntity.noContent().build();
    }

}