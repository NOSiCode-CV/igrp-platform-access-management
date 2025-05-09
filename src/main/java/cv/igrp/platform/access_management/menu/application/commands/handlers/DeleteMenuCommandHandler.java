package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.commands.commands.DeleteMenuCommand;

/**
 * Command handler responsible for processing the deletion of a {@link MenuEntry}.
 * <p>
 * This handler performs a soft delete by updating the {@link Status} of the menu entry
 * to {@code DELETED} instead of physically removing it from the database.
 * </p>
 *
 * It throws an {@link IgrpResponseStatusException} with an {@link IgrpProblem} if the specified
 * menu entry is not found.
 *
 */
@Service
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<Void>> {

    private final MenuEntryRepository menuEntryRepository;
    private final Logger logger = LoggerFactory.getLogger(DeleteMenuCommandHandler.class);

    /**
     * Constructs a new {@code DeleteMenuCommandHandler} with the required dependencies.
     *
     * @param menuEntryRepository the repository used to access and update {@link MenuEntry} records
     */
    public DeleteMenuCommandHandler(MenuEntryRepository menuEntryRepository) {
        this.menuEntryRepository = menuEntryRepository;
    }

    /**
     * Handles the {@link DeleteMenuCommand} by performing a soft delete on the specified menu entry.
     * <p>
     * The menu entry is not physically deleted from the database. Instead, its status is updated
     * to {@link Status#DELETED}, allowing it to be ignored or filtered out in application logic.
     * </p>
     *
     * @param command the command containing the ID of the menu to delete
     * @return {@link ResponseEntity} with HTTP 204 No Content if deletion is successful
     * @throws IgrpResponseStatusException if the menu entry is not found
     */
    @IgrpCommandHandler
    public ResponseEntity<Void> handle(DeleteMenuCommand command) {

        MenuEntry menuEntry = menuEntryRepository.findById(command.getId())
                .orElseThrow(() -> new IgrpResponseStatusException(
                        new IgrpProblem<>(HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with id: " + command.getId())
                ));

        menuEntry.setStatus(Status.DELETED);
        menuEntryRepository.save(menuEntry);

        logger.info("Menu with id {} has been marked as deleted", command.getId());
        return ResponseEntity.noContent().build();
    }
}