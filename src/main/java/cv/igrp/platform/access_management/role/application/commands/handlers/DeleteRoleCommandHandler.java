package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.DeleteRoleCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
public class DeleteRoleCommandHandler implements CommandHandler<DeleteRoleCommand, ResponseEntity<Boolean>> {

    private final RoleRepository repository;

    public DeleteRoleCommandHandler(RoleRepository repository) {

        this.repository = repository;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<Boolean> handle(DeleteRoleCommand command) {
        log.info("Delete Role with id: {}.", command.getId());
        Role role = repository.findById(command.getId())
                .orElseThrow(() -> {
                    log.warn("Role with id: {} not found.", command.getId());
                    return new IgrpResponseStatusException(
                            new IgrpProblem<>(HttpStatus.NOT_FOUND, "Delete Role", "Role with id: " + command.getId() + " not found.")
                    );
                });
        role.setStatus(Status.DELETED);
        if (role.getParent() == null) {
            List<Role> roleChildList = repository.findByParent(role);
            roleChildList.stream()
                    .filter(roleChild -> !roleChild.getStatus().equals(Status.DELETED))
                    .forEach(roleChild -> roleChild.setStatus(Status.DELETED));
        }
        repository.save(role);
        log.info("Role with id: {} deleted successfully.", command.getId());
        return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
    }

}