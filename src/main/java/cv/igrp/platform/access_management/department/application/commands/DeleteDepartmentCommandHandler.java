package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
   private final IAdapter adapter;

   /**
    * Constructs a new instance of {@code DeleteDepartmentCommandHandler} with the given repository.
    *
    * @param departmentRepository the repository used to access and delete departments
    * @param adapter               the adapter interface used to interact with the external Identity and Access Management (IAM) system
    */
   public DeleteDepartmentCommandHandler(DepartmentEntityRepository departmentRepository, IAdapter adapter) {
      this.departmentRepository = departmentRepository;
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

      departmentRepository.deleteByCode(code);

      try {
         adapter.deleteDepartment(code);
      } catch (IAMException e) {
         throw new RuntimeException(e);
      }
      logger.info("Successfully deleted department with code={}", code);
      return ResponseEntity.noContent().build();
   }

}