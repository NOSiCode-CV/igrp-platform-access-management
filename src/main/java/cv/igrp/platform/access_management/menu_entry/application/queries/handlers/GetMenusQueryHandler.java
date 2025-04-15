package cv.igrp.platform.access_management.menu_entry.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu_entry.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu_entry.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu_entry.application.queries.queries.GetMenusQuery;
import java.util.List;

@Service
public class GetMenusQueryHandler implements QueryHandler<GetMenusQuery, ResponseEntity<List<MenuEntryDTO>>>{

   private MenuEntryRepository menuEntryRepository;
   private MenuEntryMapper menuEntryMapper;


   public GetMenusQueryHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
      this.menuEntryRepository =  menuEntryRepository;
      this.menuEntryMapper =  menuEntryMapper;
   }

   @IgrpQueryHandler
   public ResponseEntity<List<MenuEntryDTO>> handle(GetMenusQuery query) {
      Specification<MenuEntry> spec = buildMenuEntrySpecification(query.getName(), query.getApplicationId(), query.getType());
      List<MenuEntry> menus =  menuEntryRepository.findAll(spec);
      List<MenuEntryDTO> menuEntryDTOs = menus.stream()
              .map(menuEntryMapper::toDTO)
              .toList();
      return ResponseEntity.ok(menuEntryDTOs);
   }

   private Specification<MenuEntry> buildMenuEntrySpecification(String name, Integer applicationId, String type) {
      Specification<MenuEntry> spec = Specification.where(null);
      if (name != null && !name.isEmpty()) {
         spec = spec.and((root, query, cb) ->
                 cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
         );
      }
      if (type != null) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("type"), MenuEntryType.valueOf(type))
         );
      }
      if (applicationId != null) {
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("applicationId").get("id"), applicationId)
         );
      }
      return spec;
   }

}
