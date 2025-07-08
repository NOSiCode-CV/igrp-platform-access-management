package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.department.application.commands.commands.DeleteDepartmentCommand;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * {@link CommandHandler} implementation responsible for handling the {@link DeleteDepartmentCommand}.
 * <p>
 * This handler verifies if a department with the provided ID exists.
 * If it does, the department is deleted. If not, an {@link IgrpResponseStatusException} is thrown.
 * </p>
 *
 * <p><strong>Response:</strong> Returns HTTP 204 (No Content) if the deletion is successful.</p>
 *
 */
@Service
public class DeleteDepartmentCommandHandler implements CommandHandler<DeleteDepartmentCommand, ResponseEntity<Void>> {

    private static final Logger logger =
            LoggerFactory.getLogger(DeleteDepartmentCommandHandler.class);

    private final DepartmentRepository departmentRepository;

    /**
     * Constructs a new instance of {@code DeleteDepartmentCommandHandler} with the given repository.
     *
     * @param departmentRepository the repository used to access and delete departments
     */
    public DeleteDepartmentCommandHandler(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
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
    public ResponseEntity<Void> handle(DeleteDepartmentCommand command) {
        Integer id = command.getId();

        logger.info("Attempting to delete department with id={}", id);

        if (!departmentRepository.existsById(id)) {
            logger.warn("Department with id={} not found", id);
            throw IgrpResponseStatusException.of(HttpStatus.NOT_FOUND,
                    "Invalid Department ID", "Department not found with id: " + id);
        }

        departmentRepository.deleteById(id);

        logger.info("Successfully deleted department with id={}", id);
        return ResponseEntity.noContent().build();
    }
}