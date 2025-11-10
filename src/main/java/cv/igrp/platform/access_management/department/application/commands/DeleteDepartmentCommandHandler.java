package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link CommandHandler} implementation responsible for handling the {@link cv.igrp.platform.access_management.department.application.commands.DeleteDepartmentCommand}.
 * <p>
 * This handler verifies if a department with the provided ID exists.
 * If it does, the department is deleted. If not, an {@link IgrpResponseStatusException} is thrown.
 * </p>
 *
 * <p><strong>Response:</strong> Returns HTTP 204 (No Content) if the deletion is successful.</p>
 *
 */
@Component
public class DeleteDepartmentCommandHandler implements CommandHandler<DeleteDepartmentCommand, ResponseEntity<?>> {

   private static final Logger logger =
           LoggerFactory.getLogger(DeleteDepartmentCommandHandler.class);

   private final DepartmentEntityRepository departmentRepository;
   private final RoleEntityRepository roleRepository;
   private final IAdapter adapter;

   /**
    * Constructs a new instance of {@code DeleteDepartmentCommandHandler} with the given repository.
    *
    * @param departmentRepository the repository used to access and delete departments
    * @param roleRepository the repository used to access and delete roles
    * @param adapter               the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public DeleteDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, RoleEntityRepository roleRepository, IAdapter adapter) {
      this.departmentRepository = departmentRepository;
      this.roleRepository = roleRepository;
      this.adapter = adapter;
   }

   /**
    * Handles the delete department command.
    * <p>
    * If the department exists, it will be deleted. Otherwise, this method throws
    * an {@link IgrpResponseStatusException}.
    * </p>
    *
    * @param command the command containing the ID of the department to delete
    * @return HTTP 204 No Content if deletion was successful
    * @throws IgrpResponseStatusException if no department is found with the specified ID
    */
   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<Void> handle(DeleteDepartmentCommand command) {
      String code = command.getCode();

      logger.info("Attempting to delete department with code={}", code);

      if (!departmentRepository.existsByCode(code)) {
         logger.warn("Department with code={} not found", code);
         throw IgrpResponseStatusException.of(HttpStatus.NOT_FOUND,
                 "Invalid Department Code", "Department not found with code: " + code);
      }

      // Verify if the department doesn't have children. If it has, prevent deletion.
      var department = departmentRepository.findByCodeAndStatusNot(code, DepartmentStatus.DELETED)
                      .orElseThrow(() -> {
                         logger.warn("Department with code={} not found", code);
                         return IgrpResponseStatusException.of(HttpStatus.NOT_FOUND,
                                 "Invalid Department Code", "Department not found with code: " + code);
                      });

      deleteDepartmentRoles(department);

      if(!department.getChildrenids().isEmpty()) {
            deleteChildDepartments(department);
      }

      department.setStatus(DepartmentStatus.DELETED);

      departmentRepository.save(department);

      try {
         adapter.deleteDepartment(code);
      } catch (IAMException e) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.INTERNAL_SERVER_ERROR,
                 "Department Deletion Failed",
                 e.getMessage()
         );
      }
      logger.info("Successfully deleted department with code={}", code);
      return ResponseEntity.noContent().build();
   }

   private void deleteDepartmentRoles(DepartmentEntity department) {

      var roles = department.getRoles();

       for (RoleEntity role : roles) {
           if (role.getStatus().equals(Status.DELETED)) continue;
           var roleEntity = roleRepository.findByCodeAndStatusNotDeleted(role.getCode());
           roleEntity.setStatus(Status.DELETED);
           deleteChildRoles(roleEntity);
           roleRepository.save(roleEntity);
       }

   }

   private void deleteChildDepartments(DepartmentEntity department) {

      for (var child : department.getChildrenids()) {

         if(child.getStatus().equals(DepartmentStatus.DELETED)) continue;

         var childDepartment = departmentRepository.findByCodeAndStatusNotDeleted(child.getCode());

         deleteDepartmentRoles(childDepartment);

         if(!childDepartment.getChildrenids().isEmpty()) {
            deleteChildDepartments(childDepartment);
         }

         childDepartment.setStatus(DepartmentStatus.DELETED);
         departmentRepository.save(childDepartment);

      }

   }

   private void deleteChildRoles(RoleEntity role) {

      if (role == null) return;

      var children = role.getChildren();
      if (children == null || children.isEmpty()) return;

      for (var child : children) {
         if (child == null) continue;

         // Recurse down the existing graph instead of reloading from the repository to avoid NPEs
         deleteChildRoles(child);

         if (!Status.DELETED.equals(child.getStatus())) {
            child.setStatus(Status.DELETED);
         }
         roleRepository.save(child);
      }

   }

}