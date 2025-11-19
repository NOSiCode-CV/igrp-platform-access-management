package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;


@Component
public class UnlinkResourceFromApplicationCommandHandler implements CommandHandler<UnlinkResourceFromApplicationCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(UnlinkResourceFromApplicationCommandHandler.class);

   private final ApplicationEntityRepository applicationEntityRepository;
   private final ResourceEntityRepository resourceEntityRepository;

   public UnlinkResourceFromApplicationCommandHandler(ApplicationEntityRepository applicationEntityRepository, ResourceEntityRepository resourceEntityRepository) {
      this.applicationEntityRepository = applicationEntityRepository;
      this.resourceEntityRepository = resourceEntityRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(UnlinkResourceFromApplicationCommand command) {

      var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var resourceName : command.getUnlinkResourceFromApplicationRequest()) {

         var resource = resourceEntityRepository.findByNameNotDeleted(resourceName);

         application.getResources().remove(resource);

      }

      applicationEntityRepository.save(application);
      return ResponseEntity.noContent().build();

   }

}