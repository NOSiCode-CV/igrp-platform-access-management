package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenuByIdQuery;

/**
 * Query handler responsible for retrieving a {@link MenuEntry} by its ID
 * and converting it into a {@link MenuEntryDTO} for external consumption.
 * <p>
 * If the requested menu entry is not found, a custom {@link IgrpResponseStatusException}
 * is thrown with a 404 Not Found status and a detailed error message.
 * </p>
 *
 */
@Service
public class GetMenuByIdQueryHandler implements QueryHandler<GetMenuByIdQuery, ResponseEntity<MenuEntryDTO>>{

   private static final Logger logger = LoggerFactory.getLogger(GetMenuByIdQueryHandler.class);

   private final MenuEntryRepository menuEntryRepository;
   private final MenuEntryMapper menuEntryMapper;

   /**
    * Constructs a new {@code GetMenuByIdQueryHandler} with the required dependencies.
    *
    * @param menuEntryRepository the repository used to fetch {@link MenuEntry} entities from the data source
    * @param menuEntryMapper the mapper used to convert {@link MenuEntry} entities into {@link MenuEntryDTO} objects
    */
   public GetMenuByIdQueryHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
      this.menuEntryRepository =  menuEntryRepository;
      this.menuEntryMapper =  menuEntryMapper;
   }

   /**
    * Handles the {@link GetMenuByIdQuery} by retrieving the specified {@link MenuEntry}
    * and returning it as a {@link MenuEntryDTO}.
    * <p>
    * If no menu entry is found for the provided ID, an {@link IgrpResponseStatusException}
    * is thrown with an HTTP 404 Not Found status and a detailed error description.
    * </p>
    *
    * @param query the query containing the menu entry ID
    * @return a {@link ResponseEntity} containing the {@link MenuEntryDTO} if found
    * @throws IgrpResponseStatusException if the menu entry is not found
    */
   @IgrpQueryHandler
   public ResponseEntity<MenuEntryDTO> handle(GetMenuByIdQuery query) {
      MenuEntry menu = menuEntryRepository.findById(query.getId())
              .orElseThrow(() -> {
                 logger.warn("Menu not found with ID: {}", query.getId());
                 return IgrpResponseStatusException.of(
                         HttpStatus.NOT_FOUND,
                         "Menu not found",
                         "Menu not found with id: " + query.getId());
              });

      logger.info("Menu with ID: {} successfully retrieved", query.getId());
      return ResponseEntity.ok(menuEntryMapper.toDTO(menu));
   }
}