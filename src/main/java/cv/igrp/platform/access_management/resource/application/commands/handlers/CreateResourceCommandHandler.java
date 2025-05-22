package cv.igrp.platform.access_management.resource.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.resource.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.domain.models.ResourceItem;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.resource.application.commands.commands.CreateResourceCommand;
import java.util.List;

/**
 * {@code CreateResourceCommandHandler} is a command handler responsible for creating new
 * {@link Resource} entities based on input received via a {@link CreateResourceCommand}.
 * <p>
 * This handler performs the following actions:
 * <ul>
 *   <li>Maps the incoming {@link ResourceDTO} to a {@link Resource} domain model</li>
 *   <li>Sets the initial status of the resource to {@link Status#ACTIVE}</li>
 *   <li>Validates and links the associated {@link Application}</li>
 *   <li>Maps and links any associated {@link ResourceItem} permissions if provided</li>
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
@Service
public class CreateResourceCommandHandler implements
        CommandHandler<CreateResourceCommand, ResponseEntity<ResourceDTO>> {

    private static final Logger logger =
            LoggerFactory.getLogger(CreateResourceCommandHandler.class);

   private final ResourceRepository resourceRepository;
   private final PermissionRepository permissionRepository;
   private final ApplicationRepository applicationRepository;
   private final ResourceMapper resourceMapper;

    /**
     * Constructs a new {@code CreateResourceCommandHandler} with the required repositories and mapper.
     *
     * @param resourceRepository     the repository used to persist {@link Resource} entities
     * @param applicationRepository  the repository used to resolve linked {@link Application} entities
     * @param resourceMapper         the mapper to convert between DTOs and domain models
     * @param permissionRepository   the repository used to validate {@link Permission} references
     */
   public CreateResourceCommandHandler(
           ResourceRepository resourceRepository,
           ApplicationRepository applicationRepository,
           ResourceMapper resourceMapper,
           PermissionRepository permissionRepository) {
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

       Resource resource = resourceMapper.toEntity(resourceDTO);
       resource.setStatus(Status.ACTIVE);

      Application application = applicationRepository.findById(resourceDTO.getApplicationId())
              .orElseThrow(() -> {
                  logger.warn("Application not found with id: {}", resourceDTO.getApplicationId());
                  return new IgrpResponseStatusException(new IgrpProblem<>(HttpStatus.NOT_FOUND,
                          "Application not found",
                          "Application not found with id: " +resourceDTO.getApplicationId()));
              });

      resource.setApplicationId(application);

      if (resourceDTO.getItems() != null && !resourceDTO.getItems().isEmpty()) {
         List<ResourceItem> items = resourceDTO.getItems().stream()
                 .map(itemDTO -> {
                    Permission permission = permissionRepository.findById(itemDTO.getPermissionId())
                            .orElseThrow(() -> {
                                logger.warn("Permission not found with id: {}", itemDTO.getPermissionId());
                                return new IgrpResponseStatusException(new IgrpProblem<>(HttpStatus.NOT_FOUND,
                                        "Permission not found",
                                        "Permission not found with id: " + itemDTO.getPermissionId()));
                            });
                     return resourceMapper.toItemEntity(itemDTO, resource, permission);
                 }).toList();
         resource.setItems(items);
         logger.info("Mapped {} permission item(s) for resource.", items.size());
      } else {
          logger.info("No permission items provided for this resource.");
      }

       Resource savedResource = resourceRepository.save(resource);
       logger.info("Resource created successfully with id: {}", savedResource.getId());

       ResourceDTO responseDto = resourceMapper.toDto(savedResource);
       return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
   }

}