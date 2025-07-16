package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceItemEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


/**
 * {@code CreateResourceCommandHandler} is a command handler responsible for creating new
 * {@link ResourceEntity} entities based on input received via a {@link CreateResourceCommand}.
 * <p>
 * This handler performs the following actions:
 * <ul>
 *   <li>Maps the incoming {@link ResourceDTO} to a {@link ResourceEntity} domain model</li>
 *   <li>Sets the initial status of the resource to {@link Status#ACTIVE}</li>
 *   <li>Validates and links the associated {@link ApplicationEntity}</li>
 *   <li>Maps and links any associated {@link ResourceItemEntity} permissions if provided</li>
 *   <li>Persists the resource entity and returns a response with the saved DTO</li>
 * </ul>
 * <p>
 * If the referenced application or permission is not found, an {@link IgrpResponseStatusException}
 * is thrown with appropriate details.
 *
 * @see CommandHandler
 * @see CreateResourceCommand
 * @see ResourceDTO
 */
@Component
public class CreateResourceCommandHandler implements CommandHandler<CreateResourceCommand, ResponseEntity<ResourceDTO>> {

   private static final Logger logger =
           LoggerFactory.getLogger(CreateResourceCommandHandler.class);

   private final ResourceEntityRepository resourceRepository;
   private final PermissionEntityRepository permissionRepository;
   private final ApplicationEntityRepository applicationRepository;
   private final ResourceMapper resourceMapper;

   /**
    * Constructs a new {@code CreateResourceCommandHandler} with the required repositories and mapper.
    *
    * @param resourceRepository     the repository used to persist {@link ResourceEntity} entities
    * @param applicationRepository  the repository used to resolve linked {@link ApplicationEntity} entities
    * @param resourceMapper         the mapper to convert between DTOs and domain models
    * @param permissionRepository   the repository used to validate {@link PermissionEntity} references
    */
   public CreateResourceCommandHandler(
           ResourceEntityRepository resourceRepository,
           ApplicationEntityRepository applicationRepository,
           ResourceMapper resourceMapper,
           PermissionEntityRepository permissionRepository) {
      this.resourceRepository = resourceRepository;
      this.applicationRepository = applicationRepository;
      this.resourceMapper = resourceMapper;
      this.permissionRepository = permissionRepository;
   }

   /**
    * Handles the {@link CreateResourceCommand} by validating, mapping, and persisting the resource.
    * <p>
    * If an associated application or permission ID cannot be found, a structured business exception
    * is thrown with details using {@link IgrpResponseStatusException}.
    *
    * @param command the command containing the {@link ResourceDTO} to create
    * @return a {@link ResponseEntity} with status {@code 201 Created} and the saved {@link ResourceDTO}
    * @throws IgrpResponseStatusException if application or permission IDs are invalid
    */
   @IgrpCommandHandler
   public ResponseEntity<ResourceDTO> handle(CreateResourceCommand command) {

      var resourceDTO = command.getResourcedto();

      logger.info("Creating resource with applicationId: {}", resourceDTO.getApplicationId());

      ResourceEntity resource = resourceMapper.toEntity(resourceDTO);

      ApplicationEntity application = applicationRepository.findById(resourceDTO.getApplicationId())
              .orElseThrow(() -> {
                 logger.warn("Application not found with id: {}", resourceDTO.getApplicationId());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Application not found",
                         "Application not found with id: " + resourceDTO.getApplicationId());
              });

      resource.setApplicationId(application);

      if (resourceDTO.getItems() != null && !resourceDTO.getItems().isEmpty()) {
         List<ResourceItemEntity> items = resourceDTO.getItems().stream()
                 .map(itemDTO -> {
                    PermissionEntity permission = permissionRepository.findById(itemDTO.getPermissionId())
                            .orElseThrow(() -> {
                               logger.warn("Permission not found with id: {}", itemDTO.getPermissionId());
                               return IgrpResponseStatusException.of(
                                       HttpStatus.NOT_FOUND,
                                       "Permission not found",
                                       "Permission not found with id: " + itemDTO.getPermissionId());
                            });
                    return resourceMapper.toItemEntity(itemDTO, resource, permission);
                 }).toList();
         resource.setItems(items);
         logger.info("Mapped {} permission item(s) for resource.", items.size());
      } else {
         logger.info("No permission items provided for this resource.");
      }

      ResourceEntity savedResource = resourceRepository.save(resource);
      logger.info("Resource created successfully with id: {}", savedResource.getId());

      ResourceDTO responseDto = resourceMapper.toDto(savedResource);
      return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
   }


}