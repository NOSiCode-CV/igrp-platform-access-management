package cv.igrp.platform.access_management.role.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
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
 * @see RoleEntity
 * @see RoleEntityRepository
 * @see Status
 * @see IgrpResponseStatusException
 */
@Slf4j
@Component
public class DeleteRoleCommandHandler implements CommandHandler<DeleteRoleCommand, ResponseEntity<Boolean>> {

   private final RoleEntityRepository roleRepository;
   private final IAdapter adapter;

   /**
    * Constructs a new instance of {@code DeleteRoleCommandHandler} with the required dependencies.
    *
    * @param roleRepository repository for accessing and updating role entities
    * @param adapter the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public DeleteRoleCommandHandler(RoleEntityRepository roleRepository, IAdapter adapter) {
      this.roleRepository = roleRepository;
       this.adapter = adapter;
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
      log.info("Delete Role with name: {}.", command.getName());
      RoleEntity role = roleRepository.findByName(command.getName())
              .orElseThrow(() -> {
                 log.warn("Role with name: {} not found.", command.getName());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Delete Role", "Role with name: " + command.getName() + " not found."
                 );
              });
      role.setStatus(Status.DELETED);
      List<RoleEntity> roleChildList = roleRepository.findByParent(role);
      if(roleChildList != null){
         roleChildList.stream()
                 .filter(roleChild -> !roleChild.getStatus().equals(Status.DELETED))
                 .forEach(roleChild -> roleChild.setStatus(Status.DELETED));
      }
      roleRepository.save(role);
      try {
         adapter.deleteRole(role.getDepartment().getCode(), role.getName());
      } catch (IAMException e) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "Role Deletion Failed",
                 e.getMessage()
         );
      }

      log.info("Role with name: {} deleted successfully.", command.getName());
      return new ResponseEntity<>(true, HttpStatus.NO_CONTENT);
   }

}