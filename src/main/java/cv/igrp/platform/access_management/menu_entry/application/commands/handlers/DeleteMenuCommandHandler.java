package cv.igrp.platform.access_management.menu_entry.application.commands.handlers;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.menu_entry.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.commands.commands.DeleteMenuCommand;



@Service
public class DeleteMenuCommandHandler implements CommandHandler<DeleteMenuCommand, ResponseEntity<String>> {

   private MenuEntryRepository menuEntryRepository;

   public DeleteMenuCommandHandler(MenuEntryRepository menuEntryRepository) {
      this.menuEntryRepository =  menuEntryRepository;
   }

   @IgrpCommandHandler
   public ResponseEntity<String> handle(DeleteMenuCommand command) {
      MenuEntry menuEntry = menuEntryRepository.findById(command.getId())
              .orElseThrow(() -> new EntityNotFoundException("Menu not found with id: " + command.getId()));
      menuEntry.setStatus(Status.DELETED);
      return ResponseEntity.noContent().build();
   }

}