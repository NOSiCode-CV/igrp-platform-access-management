package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;


@Component
public class RemoveRoleFromApplicationCommandHandler implements CommandHandler<RemoveRoleFromApplicationCommand, ResponseEntity<String>> {

    private final ApplicationEntityRepository applicationRepository;
    private final RoleEntityRepository roleEntityRepository;
    private final DepartmentEntityRepository departmentEntityRepository;

    public RemoveRoleFromApplicationCommandHandler(ApplicationEntityRepository applicationRepository, RoleEntityRepository roleEntityRepository, DepartmentEntityRepository departmentEntityRepository) {
        this.departmentEntityRepository = departmentEntityRepository;
        this.applicationRepository = applicationRepository;
        this.roleEntityRepository = roleEntityRepository;
    }

    @IgrpCommandHandler
    public ResponseEntity<String> handle(RemoveRoleFromApplicationCommand command) {

        var application = applicationRepository.findByCodeAndStatusNotDeleted(command.getCode());
        var department = departmentEntityRepository.findByCodeAndStatusNotDeleted(command.getDepartmentCode());

        for (var roleName : command.getCodelistrequestdto().getCodes()) {

            var role = roleEntityRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleName);

            if (role == null) {
                throw new IllegalArgumentException("Role not found for department: " + department.getCode() + " and role name: " + roleName);
            }

            application.getRoles().remove(role);
        }

        applicationRepository.save(application);

        return ResponseEntity.noContent().build();
    }
}