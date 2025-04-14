package cv.igrp.platform.access_management.menu_entry.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu_entry.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.commands.commands.UpdateMenuCommand;



@Service
public class UpdateMenuCommandHandler implements CommandHandler<UpdateMenuCommand, ResponseEntity<MenuEntryDTO>> {

   private MenuEntryRepository menuEntryRepository;
   private MenuEntryMapper menuEntryMapper;
   private ApplicationRepository applicationRepository;
   private ResourceRepository resourceRepository;

   public UpdateMenuCommandHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, ApplicationRepository applicationRepository, ResourceRepository resourceRepository) {
      this.menuEntryRepository = menuEntryRepository;
      this.applicationRepository = applicationRepository;
      this.resourceRepository = resourceRepository;
      this.menuEntryMapper = menuEntryMapper;
   }

   @IgrpCommandHandler
   public ResponseEntity<MenuEntryDTO> handle(UpdateMenuCommand command) {
      MenuEntry menuEntry = menuEntryRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Menu not found"));
      menuEntry.setName(command.getMenuentrydto().getName());
      menuEntry.setType(command.getMenuentrydto().getType());
      menuEntry.setPosition(command.getMenuentrydto().getPosition());
      menuEntry.setIcon(command.getMenuentrydto().getIcon());
      menuEntry.setStatus(command.getMenuentrydto().getStatus());
      menuEntry.setTarget(command.getMenuentrydto().getTarget());
      menuEntry.setUrl(command.getMenuentrydto().getUrl());
      if (command.getMenuentrydto().getParentId() != null)
         menuEntry.setParentId(menuEntryRepository.findById(command.getMenuentrydto().getParentId()).orElseThrow(() -> new EntityNotFoundException("Parent MenuEntry not found")));
      if (command.getMenuentrydto().getResourceId() != null)
         menuEntry.setResourceId(resourceRepository.findById(command.getMenuentrydto().getResourceId()).orElseThrow(() -> new EntityNotFoundException("Resource not found")));
      if (command.getMenuentrydto().getApplicationId() != null)
         menuEntry.setApplicationId(applicationRepository.findById(command.getMenuentrydto().getApplicationId()).orElseThrow(() -> new EntityNotFoundException("Application not found")));
      return ResponseEntity.ok(menuEntryMapper.toDTO(menuEntryRepository.save(menuEntry)));
   }

}