package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Query handler responsible for retrieving a {@link MenuEntryEntity} by its ID
 * and converting it into a {@link MenuEntryDTO} for external consumption.
 * <p>
 * If the requested menu entry is not found, a custom {@link IgrpResponseStatusException}
 * is thrown with a 404 Not Found status and a detailed error message.
 * </p>
 *
 */
@Component
public class GetMenuByIdQueryHandler implements QueryHandler<GetMenuByIdQuery, ResponseEntity<MenuEntryDTO>>{

  private static final Logger logger = LoggerFactory.getLogger(GetMenuByIdQueryHandler.class);

  private final MenuEntryEntityRepository menuEntryRepository;
  private final MenuEntryMapper menuEntryMapper;

  /**
   * Constructs a new {@code GetMenuByIdQueryHandler} with the required dependencies.
   *
   * @param menuEntryRepository the repository used to fetch {@link MenuEntryEntity} entities from the data source
   * @param menuEntryMapper the mapper used to convert {@link MenuEntryEntity} entities into {@link MenuEntryDTO} objects
   */
  public GetMenuByIdQueryHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
    this.menuEntryRepository =  menuEntryRepository;
    this.menuEntryMapper =  menuEntryMapper;
  }

  /**
   * Handles the {@link cv.igrp.platform.access_management.menu.application.queries.GetMenuByIdQuery} by retrieving the specified {@link MenuEntryEntity}
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
    MenuEntryEntity menu = menuEntryRepository.findById(query.getId())
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