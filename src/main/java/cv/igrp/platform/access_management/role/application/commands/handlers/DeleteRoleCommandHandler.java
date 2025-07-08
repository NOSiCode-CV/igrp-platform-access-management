package cv.igrp.platform.access_management.role.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.application.commands.commands.DeleteRoleCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Command handler responsible for processing {@link DeleteRoleCommand}.
 * <p>
 * When a role is deleted, it is not physically removed from the database. Instead,
 * its {@link Status} is set to {@code DELETED}, indicating a soft delete.
 * <p>
 * If the role has no parent (i.e., it is a root role), this handler will also
 * cascade the deletion status to all its non-deleted child roles.
 * <p>
 * This ensures integrity of role hierarchies and prevents orphaned child roles.
 * @see DeleteRoleCommand
 * @see Role
 * @see RoleRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Service
public class DeleteRoleCommandHandler implements CommandHandler<DeleteRoleCommand, ResponseEntity<Boolean>> {

    private final RoleRepository roleRepository;

    /**
     * Constructs a new instance of {@code DeleteRoleCommandHandler} with the required dependencies.
     *
     * @param roleRepository repository for accessing and updating role entities
     */
    public DeleteRoleCommandHandler(RoleRepository roleRepository) {

        this.roleRepository = roleRepository;
    }

    /**
     * Handles the deletion of a role identified by the given {@link DeleteRoleCommand}.
     * <p>
     * The deletion is logical (soft delete), meaning the role's status is set to {@code DELETED}
     * rather than being physically removed from the database.
     * <p>
     * If the role is a root (i.e., has no parent), all its child roles with status different from
     * {@code DELETED} will also be soft-deleted.
     *
     * @param command is the command containing the ID of the role to delete
     * @return a {@link ResponseEntity} containing {@code true} with status {@code 204 NO_CONTENT} if successful
     * @throws IgrpResponseStatusException if the role with the specified ID is not found
     */
    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<Boolean> handle(DeleteRoleCommand command) {
        log.info("Delete Role with id: {}.", command.getId());
        Role role = roleRepository.findById(command.getId())
                .orElseThrow(() -> {
                    log.warn("Role with id: {} not found.", command.getId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Delete Role", "Role with id: " + command.getId() + " not found."
                    );
                });
        role.setStatus(Status.DELETED);
        List<Role> roleChildList = roleRepository.findByParent(role);
        if(roleChildList != null){
            roleChildList.stream()
                    .filter(roleChild -> !roleChild.getStatus().equals(Status.DELETED))
                    .forEach(roleChild -> roleChild.setStatus(Status.DELETED));
        }

        roleRepository.save(role);
        log.info("Role with id: {} deleted successfully.", command.getId());
        return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
    }
}