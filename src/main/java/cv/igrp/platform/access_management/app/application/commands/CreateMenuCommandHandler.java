package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.app.domain.service.MenuEntryValidator;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Command handler responsible for creating new {@link MenuEntryEntity} entities based on the input
 * provided via {@link CreateMenuCommand}.
 * <p>
 * <ul>
 *   <li>Validates the incoming {@link MenuEntryDTO}</li>
 *   <li>Maps the DTO to a {@link MenuEntryEntity} domain entity</li>
 *   <li>Resolves and assigns related entities (application, resource, parent menu)</li>
 *   <li>Persists the new menu entry using {@link MenuEntryEntityRepository}</li>
 *   <li>Returns the persisted entity as a DTO with HTTP 201 Created status</li>
 * </ul>
 * </p>
 * <p>
 * If any of the referenced foreign key relationships (application, resource, or parent menu) are invalid,
 * the handler throws a {@link IgrpResponseStatusException}
 * </p>
 *
 */
@Component
public class CreateMenuCommandHandler implements CommandHandler<CreateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private static final Logger logger = LoggerFactory.getLogger(CreateMenuCommandHandler.class);

   private final MenuEntryEntityRepository menuEntryRepository;
   private final MenuEntryMapper menuEntryMapper;
   private final ApplicationEntityRepository applicationRepository;
    private final MenuEntryValidator menuEntryValidator;

   /**
    * Constructs a {@code CreateMenuCommandHandler} with all required dependencies.
    *
    * @param menuEntryRepository the repository used to persist {@link MenuEntryEntity} entities
    * @param menuEntryMapper the mapper used to convert between entity and DTO
    * @param applicationRepository repository to resolve associated {@link ApplicationEntity}
    */
   public CreateMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper,
                                   ApplicationEntityRepository applicationRepository,
                                   MenuEntryValidator menuEntryValidator) {
      this.menuEntryRepository = menuEntryRepository;
      this.menuEntryMapper = menuEntryMapper;
      this.applicationRepository = applicationRepository;
       this.menuEntryValidator = menuEntryValidator;
   }

   /**
    * Handles the creation of a new {@link MenuEntryEntity} from the data provided in the {@link CreateMenuCommand}.
    * <p>
    * This method performs the following steps:
    * <ul>
    *   <li>Validates that the {@link MenuEntryDTO} is not null</li>
    *   <li>Maps the DTO to a {@link MenuEntryEntity} entity</li>
    *   <li>Resolves and sets relationships: application, resource, and parent menu</li>
    *   <li>Persists the new menu entity</li>
    *   <li>Logs creation success or failure at appropriate points</li>
    * </ul>
    * </p>
    *
    * <p>
    * If any of the related entities (application, resource, or parent menu) are not found, a
    * {@link IgrpResponseStatusException}
    * is thrown with an appropriate {@link HttpStatus} and problem details.
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
         throw IgrpResponseStatusException.of(
                 HttpStatus.BAD_REQUEST, "Menu", "Menu Entry DTO Missing");
      }

      var validation = menuEntryValidator.validateMenuEntryCode(menuEntryDTO);
      if(!validation.isValid()) {
         throw IgrpResponseStatusException.of(
                 HttpStatus.CONFLICT, "Create Menu Entry", validation.getFailureMessage()
         );
      }

      MenuEntryValidator.validateRequiredFields(menuEntryDTO);

      MenuEntryEntity menuEntry = menuEntryMapper.toEntity(menuEntryDTO);

      menuEntry.setApplicationId(applicationRepository.findByCodeAndStatusNot(command.getApplicationCode(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Application not found with code: {}", menuEntryDTO.getApplicationCode());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND, "Application not found",
                         "Application not found with code: " + menuEntryDTO.getApplicationCode());
              }));

      if (menuEntryDTO.getParentCode() != null) {
         menuEntry.setParentId(menuEntryRepository.findByApplicationIdAndCodeAndStatusNot(menuEntry.getApplicationId(), menuEntryDTO.getParentCode(), Status.DELETED)
                 .orElseThrow(() -> {
                    logger.warn("Parent menu not found with code: {}", menuEntryDTO.getParentCode());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Parent Menu not found",
                            "ParentMenu not found with code: " + menuEntryDTO.getParentCode());
                 }));
      }

      var savedMenuEntry = menuEntryRepository.save(menuEntry);

      logger.info("""
                    Menu created: code={}, name={}, type={}
                    """,
              savedMenuEntry.getCode(),
              savedMenuEntry.getName(),
              savedMenuEntry.getType());

      return ResponseEntity.status(HttpStatus.CREATED).body(menuEntryMapper.toDTO(savedMenuEntry));
   }

}