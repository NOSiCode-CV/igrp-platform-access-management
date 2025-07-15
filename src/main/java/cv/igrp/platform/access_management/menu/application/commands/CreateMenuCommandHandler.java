package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
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
   private final ResourceEntityRepository resourceRepository;
   private final PermissionEntityRepository permissionRepository;

   /**
    * Constructs a {@code CreateMenuCommandHandler} with all required dependencies.
    *
    * @param menuEntryRepository the repository used to persist {@link MenuEntryEntity} entities
    * @param menuEntryMapper the mapper used to convert between entity and DTO
    * @param applicationRepository repository to resolve associated {@link ApplicationEntity}
    * @param resourceRepository repository to resolve associated {@link ResourceEntity}
    */
   public CreateMenuCommandHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper,
                                   ApplicationEntityRepository applicationRepository, ResourceEntityRepository resourceRepository,
                                   PermissionEntityRepository permissionRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.menuEntryMapper = menuEntryMapper;
      this.applicationRepository = applicationRepository;
      this.resourceRepository = resourceRepository;
      this.permissionRepository = permissionRepository;
   }

   /**
    * Handles the creation of a new {@link MenuEntryEntity} from the data provided in the {@link cv.igrp.platform.access_management.menu.application.commands.commands.CreateMenuCommand}.
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

      MenuEntryEntity menuEntry = menuEntryMapper.toEntity(menuEntryDTO);

      if (menuEntryDTO.getApplicationId() != null) {
         menuEntry.setApplicationId(applicationRepository.findById(menuEntryDTO.getApplicationId())
                 .orElseThrow(() -> {
                    logger.warn("Application not found with ID: {}", menuEntryDTO.getApplicationId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "Application not found",
                            "Application not found with id: " + menuEntryDTO.getApplicationId());
                 }));
      }

      if (menuEntryDTO.getParentId() != null) {
         menuEntry.setParentId(menuEntryRepository.findById(menuEntryDTO.getParentId())
                 .orElseThrow(() -> {
                    logger.warn("Parent menu not found with ID: {}", menuEntryDTO.getParentId());
                    return IgrpResponseStatusException.of(
                            HttpStatus.NOT_FOUND, "ParentMenu not found",
                            "ParentMenu not found with id: " + menuEntryDTO.getParentId());
                 }));
      }

      var savedMenuEntry = menuEntryRepository.save(menuEntry);

      if (menuEntryDTO.getPermissions() != null) {

         menuEntryDTO.getPermissions().forEach(perm -> {

            PermissionEntity permission = permissionRepository.findByName(perm)
                    .orElseThrow(() -> {
                       logger.warn("Resource not found with name: {}", perm);
                       return IgrpResponseStatusException.of(
                               HttpStatus.NOT_FOUND, "Resource not found",
                               "Resource not found with id: " + perm);
                    });

            permission.setMenuEntryId(menuEntryRepository.findById(menuEntryDTO.getParentId())
                    .orElseThrow(() -> {
                       logger.warn("Menu not found with ID: {}", menuEntryDTO.getParentId());
                       return IgrpResponseStatusException.of(
                               HttpStatus.NOT_FOUND, "Menu not found",
                               "Menu not found with id: " + menuEntryDTO.getParentId());
                    }));

            permissionRepository.save(permission);

         });

      }

      logger.info("""
                    Menu created: id={}, name={}, type={}
                    """,
              savedMenuEntry.getId(),
              savedMenuEntry.getName(),
              savedMenuEntry.getType());

      return ResponseEntity.status(HttpStatus.CREATED).body(menuEntryMapper.toDTO(savedMenuEntry));
   }
}