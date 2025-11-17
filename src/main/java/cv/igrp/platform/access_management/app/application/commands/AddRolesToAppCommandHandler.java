package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AddRolesToAppCommandHandler implements CommandHandler<AddRolesToAppCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleEntityRepository;
    private final DepartmentEntityRepository departmentEntityRepository;

    public AddRolesToAppCommandHandler(ApplicationEntityRepository applicationRepository, RoleEntityRepository roleEntityRepository, DepartmentEntityRepository departmentEntityRepository) {
        this.applicationRepository = applicationRepository;
        this.roleEntityRepository = roleEntityRepository;
        this.departmentEntityRepository = departmentEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(AddRolesToAppCommand command) {

        var department = departmentEntityRepository.findByCodeAndStatusNotDeleted(command.getDepartmentCode());

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());

        for (var roleName : command.getCodelistrequestdto().getCodes()) {

            var role = roleEntityRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleName);

            var roleDepartment = role.getDepartment();
            if (roleDepartment == null)
                throw IgrpResponseStatusException.internalServerError("Role department cannot be null");

            if (!application.getDepartments().contains(roleDepartment))
                throw IgrpResponseStatusException.forbidden(
                        "Cannot assign role '%s' because its department '%s' is not assigned to the application '%s'".formatted(
                                role.getName(),
                                roleDepartment.getCode(),
                                application.getCode()
                        )
                );

            application.getRoles().add(role);
        }

        applicationRepository.save(application);

        return ResponseEntity.ok().build();
    }

}