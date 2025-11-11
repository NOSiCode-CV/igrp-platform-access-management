package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * {@code DeleteResourceCommandHandler} is responsible for handling the deletion of a
 * {@link ResourceEntity} entity identified by its ID. If the resource exists, it will be removed from
 * the database. Additionally, any associated {@link CustomFieldEntity} records will also be removed.
 *
 * <p>This handler ensures consistency between the resource and its related metadata.
 * In case the resource is not found, a {@link IgrpResponseStatusException} is thrown
 *
 * <p>Logging is provided for all major operations (resource lookup, deletion,
 * and custom field cleanup).
 *
 */
@Component
public class DeleteResourceCommandHandler implements CommandHandler<DeleteResourceCommand, ResponseEntity<String>> {

   private static final Logger logger =
           LoggerFactory.getLogger(DeleteResourceCommandHandler.class);

   private final ResourceEntityRepository resourceRepository;
   private final PermissionEntityRepository permissionRepository;
   private final CustomFieldEntityRepository customFieldRepository;

   /**
    * Constructs the {@code DeleteResourceCommandHandler} with the required repositories.
    *
    * @param resourceRepository      the repository used to access and delete {@link ResourceEntity} entities
    * @param permissionRepository    the repository used to access and delete {@link PermissionEntity} entities
    * @param customFieldRepository   the repository used to access and delete {@link CustomFieldEntity} entries
    */
   public DeleteResourceCommandHandler(
           ResourceEntityRepository resourceRepository,
           PermissionEntityRepository permissionRepository,
           CustomFieldEntityRepository customFieldRepository) {
      this.resourceRepository = resourceRepository;
      this.permissionRepository = permissionRepository;
      this.customFieldRepository = customFieldRepository;
   }

   /**
    * Handles the deletion of a resource based on the provided {@link DeleteResourceCommand}.
    *
    * <p>If the resource exists, it is deleted from the database. If a custom field entry exists
    * for the same resource (using {@link CustomFieldTableName#RESOURCE}), it is also deleted.
    *
    * @param command the command containing the resource ID to be deleted
    * @return a {@link ResponseEntity} with HTTP 204 No Content if deletion was successful
    * @throws IgrpResponseStatusException if the resource with the specified ID does not exist
    */
   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteResourceCommand command) {
      String resourceName = command.getName();

      logger.info("Attempting to delete resource with name: {}", resourceName);

      ResourceEntity resource = resourceRepository.findByNameAndStatusNot(command.getName(), Status.DELETED)
              .orElseThrow(() -> {
                 logger.warn("Resource not found with name: {}", resourceName);
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Resource not found",
                         "Resource not found with name: " + resourceName);
              });

      deleteResourcePermissions(resource);

      resource.setStatus(Status.DELETED);

      resourceRepository.save(resource);
      logger.info("Deleted resource with name: {}", resourceName);

      Optional<CustomFieldEntity> customField = customFieldRepository
              .findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resource.getId());

      customField.ifPresent(field -> {
         customFieldRepository.delete(field);
         logger.info("Deleted associated custom field for resource name: {}", resourceName);
      });

      return ResponseEntity.noContent().build();
   }

   private void deleteResourcePermissions(ResourceEntity resource) {

      if(!resource.getPermissions().isEmpty()) {

         for (var perm : resource.getPermissions()) {

            if(perm.getStatus().equals(Status.DELETED)) continue;

            var permission = permissionRepository.findByNameAndStatusNot(perm.getName(), Status.DELETED).orElseThrow(() ->
                    IgrpResponseStatusException
                            .notFound("Permission with name <%s> not found".formatted(perm.getName()))
            );

            permission.setStatus(Status.DELETED);

            permissionRepository.save(permission);

         }

      }

   }

}