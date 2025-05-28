package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.commands.commands.CreateMenuCommand;


/**
 * Command handler responsible for creating new {@link MenuEntry} entities based on the input
 * provided via {@link CreateMenuCommand}.
 * <p>
 * <ul>
 *   <li>Validates the incoming {@link MenuEntryDTO}</li>
 *   <li>Maps the DTO to a {@link MenuEntry} domain entity</li>
 *   <li>Resolves and assigns related entities (application, resource, parent menu)</li>
 *   <li>Persists the new menu entry using {@link MenuEntryRepository}</li>
 *   <li>Returns the persisted entity as a DTO with HTTP 201 Created status</li>
 * </ul>
 * </p>
 * <p>
 * If any of the referenced foreign key relationships (application, resource, or parent menu) are invalid,
 * the handler throws a {@link cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException}
 * with a descriptive {@link cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem} payload.
 * </p>
 *
 */
@Service
public class CreateMenuCommandHandler implements CommandHandler<CreateMenuCommand, ResponseEntity<MenuEntryDTO>> {

    private static final Logger logger = LoggerFactory.getLogger(CreateMenuCommandHandler.class);

    private final MenuEntryRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;
    private final ApplicationRepository applicationRepository;
    private final ResourceRepository resourceRepository;

    /**
     * Constructs a {@code CreateMenuCommandHandler} with all required dependencies.
     *
     * @param menuEntryRepository the repository used to persist {@link MenuEntry} entities
     * @param menuEntryMapper the mapper used to convert between entity and DTO
     * @param applicationRepository repository to resolve associated {@link cv.igrp.platform.access_management.shared.domain.models.Application}
     * @param resourceRepository repository to resolve associated {@link cv.igrp.platform.access_management.shared.domain.models.Resource}
     */
    public CreateMenuCommandHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationRepository applicationRepository, ResourceRepository resourceRepository) {
        this.menuEntryRepository = menuEntryRepository;
        this.menuEntryMapper = menuEntryMapper;
        this.applicationRepository = applicationRepository;
        this.resourceRepository = resourceRepository;
    }

    /**
     * Handles the creation of a new {@link MenuEntry} from the data provided in the {@link CreateMenuCommand}.
     * <p>
     * This method performs the following steps:
     * <ul>
     *   <li>Validates that the {@link MenuEntryDTO} is not null</li>
     *   <li>Maps the DTO to a {@link MenuEntry} entity</li>
     *   <li>Resolves and sets relationships: application, resource, and parent menu</li>
     *   <li>Persists the new menu entity</li>
     *   <li>Logs creation success or failure at appropriate points</li>
     * </ul>
     * </p>
     *
     * <p>
     * If any of the related entities (application, resource, or parent menu) are not found, a
     * {@link cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException}
     * is thrown with an appropriate {@link org.springframework.http.HttpStatus} and problem details.
     * </p>
     *
     * @param command the command containing the {@link MenuEntryDTO} to be persisted
     * @return a {@link ResponseEntity} with HTTP 201 Created status and the created {@link MenuEntryDTO}
     * @throws IgrpResponseStatusException if the DTO is missing or related entities are not found
     */
    @IgrpCommandHandler
    public ResponseEntity<MenuEntryDTO> handle(CreateMenuCommand command) {
        MenuEntryDTO menuEntryDTO = command.getMenuentrydto();
        if (menuEntryDTO == null) {
            logger.warn("Create menu failed: Menu Entry DTO is missing");
            throw new IgrpResponseStatusException(
                    new IgrpProblem<>(HttpStatus.BAD_REQUEST, "Menu", "Menu Entry DTO Missing"));
        }

        MenuEntry menuEntry = menuEntryMapper.toEntity(menuEntryDTO);

        if (menuEntryDTO.getApplicationId() != null) {
            menuEntry.setApplicationId(applicationRepository.findById(menuEntryDTO.getApplicationId())
                    .orElseThrow(() -> {
                        logger.warn("Application not found with ID: {}", menuEntryDTO.getApplicationId());
                        return new IgrpResponseStatusException(
                                new IgrpProblem<>(HttpStatus.NOT_FOUND, "Application not found",
                                        "Application not found with id: " + menuEntryDTO.getApplicationId()));
                    }));
        }
        if (menuEntryDTO.getResourceId() != null) {
            menuEntry.setResourceId(resourceRepository.findById(menuEntryDTO.getResourceId())
                    .orElseThrow(() -> {
                        logger.warn("Resource not found with ID: {}", menuEntryDTO.getResourceId());
                        return new IgrpResponseStatusException(
                                new IgrpProblem<>(HttpStatus.NOT_FOUND, "Resource not found",
                                        "Resource not found with id: " + menuEntryDTO.getResourceId()));
                    }));
        }
        if (menuEntryDTO.getParentId() != null) {
            menuEntry.setParentId(menuEntryRepository.findById(menuEntryDTO.getParentId())
                    .orElseThrow(() -> {
                        logger.warn("Parent menu not found with ID: {}", menuEntryDTO.getParentId());
                        return new IgrpResponseStatusException(
                                new IgrpProblem<>(HttpStatus.NOT_FOUND, "ParentMenu not found",
                                        "ParentMenu not found with id: " + menuEntryDTO.getParentId()));
                    }));
        }

        var savedMenuEntry = menuEntryRepository.save(menuEntry);
        logger.info("""
                    Menu created: id={}, name={}, type={}
                    """,
                savedMenuEntry.getId(),
                savedMenuEntry.getName(),
                savedMenuEntry.getType());

        return ResponseEntity.status(HttpStatus.CREATED).body(menuEntryMapper.toDTO(savedMenuEntry));
    }
}