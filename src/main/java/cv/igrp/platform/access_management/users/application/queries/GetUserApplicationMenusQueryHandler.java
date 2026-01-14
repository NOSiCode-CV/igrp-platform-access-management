package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

@Component
public class GetUserApplicationMenusQueryHandler implements QueryHandler<GetUserApplicationMenusQuery, ResponseEntity<List<MenuEntryDTO>>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetUserApplicationMenusQueryHandler.class);

  private final MenuEntryEntityRepository menuEntryRepository;
  private final IGRPUserEntityRepository userRepository;
  private final ApplicationEntityRepository applicationRepository;
  private final MenuEntryMapper menuEntryMapper;

  public GetUserApplicationMenusQueryHandler(MenuEntryEntityRepository menuEntryRepository, IGRPUserEntityRepository userRepository, ApplicationEntityRepository applicationRepository, MenuEntryMapper menuEntryMapper) {
    this.menuEntryRepository = menuEntryRepository;
    this.userRepository = userRepository;
    this.applicationRepository = applicationRepository;
    this.menuEntryMapper = menuEntryMapper;
  }

   @IgrpQueryHandler
  public ResponseEntity<List<MenuEntryDTO>> handle(GetUserApplicationMenusQuery query) {

     var user = userRepository.findById(query.getId()).orElseThrow(
             () -> IgrpResponseStatusException.of(
                     HttpStatus.UNAUTHORIZED,
                     "User not found",
                     "User with ID: " + query.getId() + " not found in database."
             )
     );

     LOGGER.info("Getting menus for user: {}", user.getExternalId());

     var application = applicationRepository.findByCodeAndStatusNotDeleted(query.getApplicationCode());

     List<MenuEntryDTO> menuEntries = menuEntryRepository.findByApplicationIdAndUserIdAndStatusNotDeleted(Integer.valueOf(user.getId()), application.getId())
             .stream()
             .filter(it -> query.getMenuCode() == null || it.getCode().contains(query.getMenuCode()))
             .map(menuEntryMapper::toDTO)
             .toList();

     return ResponseEntity.ok(menuEntries);

  }

}