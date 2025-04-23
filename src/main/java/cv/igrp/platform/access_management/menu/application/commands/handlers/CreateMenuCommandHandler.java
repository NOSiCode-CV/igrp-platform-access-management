package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.commands.commands.CreateMenuCommand;



@Service
public class CreateMenuCommandHandler implements CommandHandler<CreateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private MenuEntryRepository menuEntryRepository;
   private MenuEntryMapper menuEntryMapper;
   private ApplicationRepository applicationRepository;
   private ResourceRepository resourceRepository;

   public CreateMenuCommandHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationRepository applicationRepository, ResourceRepository resourceRepository) {
      this.menuEntryRepository =  menuEntryRepository;
      this.menuEntryMapper =  menuEntryMapper;
      this.applicationRepository =  applicationRepository;
      this.resourceRepository =  resourceRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<MenuEntryDTO> handle(CreateMenuCommand command) {
      MenuEntry menuEntry = menuEntryMapper.toEntity(command.getMenuentrydto());
      menuEntry.setStatus(Status.ACTIVE);
      menuEntry.setApplicationId(applicationRepository.getReferenceById(command.getMenuentrydto().getApplicationId()));
      if (command.getMenuentrydto().getResourceId() != null)
         menuEntry.setResourceId(resourceRepository.getReferenceById(command.getMenuentrydto().getResourceId()));
      if (command.getMenuentrydto().getParentId() != null)
         menuEntry.setParentId(menuEntryRepository.getReferenceById(command.getMenuentrydto().getParentId()));
      return ResponseEntity.status(HttpStatus.CREATED).body(menuEntryMapper.toDTO(menuEntryRepository.save(menuEntry)));
   }

}