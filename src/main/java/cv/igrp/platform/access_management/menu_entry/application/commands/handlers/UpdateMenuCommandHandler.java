package cv.igrp.platform.access_management.menu_entry.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu_entry.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
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
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with id: " + command.getId()));
              });
      MenuEntryDTO menuDto = command.getMenuentrydto();
      menuEntry.setName(menuDto.getName());
      menuEntry.setType(menuDto.getType());
      menuEntry.setPosition(menuDto.getPosition());
      menuEntry.setIcon(menuDto.getIcon());
      menuEntry.setStatus(menuDto.getStatus());
      menuEntry.setTarget(menuDto.getTarget());
      menuEntry.setUrl(menuDto.getUrl());
      if (menuDto.getParentId() != null)
         menuEntry.setParentId(menuEntryRepository.findById(menuDto.getParentId())
                 .orElseThrow(() -> {
                    return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Parent MenuEntry not found", "Parent MenuEntry not found with id: " + menuDto.getParentId()));
                 }));
      if (menuDto.getResourceId() != null)
         menuEntry.setResourceId(resourceRepository.findById(menuDto.getResourceId())
                 .orElseThrow(() -> {
                    return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Resource not found", "Resource not found with id: " + menuDto.getResourceId()));
                 }));
      if (menuDto.getApplicationId() != null)
         menuEntry.setApplicationId(applicationRepository.findById(menuDto.getApplicationId())
                 .orElseThrow(() -> {
                    return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + menuDto.getApplicationId()));
                 }));
      return ResponseEntity.ok(menuEntryMapper.toDTO(menuEntryRepository.save(menuEntry)));
   }

}