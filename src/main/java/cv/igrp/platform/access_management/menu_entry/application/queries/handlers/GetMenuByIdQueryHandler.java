package cv.igrp.platform.access_management.menu_entry.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu_entry.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.GetMenuByIdQuery;


@Service
public class GetMenuByIdQueryHandler implements QueryHandler<GetMenuByIdQuery, ResponseEntity<MenuEntryDTO>>{

   private MenuEntryRepository menuEntryRepository;
   private MenuEntryMapper menuEntryMapper;


   public GetMenuByIdQueryHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
      this.menuEntryRepository =  menuEntryRepository;
      this.menuEntryMapper =  menuEntryMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<MenuEntryDTO> handle(GetMenuByIdQuery query) {
      MenuEntry menu = menuEntryRepository.findById(query.getId())
              .orElseThrow(() -> new EntityNotFoundException("Menu not found"));
      return ResponseEntity.ok(menuEntryMapper.toDTO(menu));
   }

}