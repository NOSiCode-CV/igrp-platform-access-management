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
public class LinkResourceToApplicationCommandHandler implements CommandHandler<LinkResourceToApplicationCommand, ResponseEntity<String>> {

   private static final Logger LOGGER = LoggerFactory.getLogger(LinkResourceToApplicationCommandHandler.class);

   private final ApplicationEntityRepository applicationEntityRepository;
   private final ResourceEntityRepository resourceEntityRepository;

   public LinkResourceToApplicationCommandHandler(ApplicationEntityRepository applicationEntityRepository, ResourceEntityRepository resourceEntityRepository) {
      this.applicationEntityRepository = applicationEntityRepository;
      this.resourceEntityRepository = resourceEntityRepository;
   }

   @IgrpCommandHandler
   @Transactional
   public ResponseEntity<String> handle(LinkResourceToApplicationCommand command) {

      var application = applicationEntityRepository.findByCodeAndStatusNotDeleted(command.getCode());

      for (var resourceName : command.getLinkResourceToApplicationRequest()) {

         var resource = resourceEntityRepository.findByNameNotDeleted(resourceName);

         application.getResources().add(resource);

      }

      applicationEntityRepository.save(application);

      return ResponseEntity.ok().build();

   }

}