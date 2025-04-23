package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenuByIdQuery;


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
              .orElseThrow(() -> {
                 return new IgrpResponseStatusException(new IgrpProblem<String>(HttpStatus.NOT_FOUND, "Menu not found", "Menu not found with id: " + query.getId()));
              });
      return ResponseEntity.ok(menuEntryMapper.toDTO(menu));
   }

}