package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;

@Component
public class GetMenusQueryHandler implements QueryHandler<GetMenusQuery, ResponseEntity<List<MenuEntryDTO>>>{

  private static final Logger logger = LoggerFactory.getLogger(GetMenusQueryHandler.class);

  private final MenuEntryEntityRepository menuEntryRepository;
  private final MenuEntryMapper menuEntryMapper;


  /**
   * Constructs a {@code GetMenusQueryHandler} with the required dependencies.
   *
   * @param menuEntryRepository the repository used to retrieve {@link MenuEntryEntity} entities
   * @param menuEntryMapper the mapper used to convert {@link MenuEntryEntity} to {@link MenuEntryDTO}
   */
  public GetMenusQueryHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
    this.menuEntryRepository =  menuEntryRepository;
    this.menuEntryMapper =  menuEntryMapper;
  }

  /**
   * Handles the {@link GetMenusQuery} by retrieving a filtered list of {@link MenuEntryDTO}s.
   * <p>
   * The filtering criteria include menu name, application ID, and menu type. These filters
   * are extracted from the {@code query} object and used to build a dynamic {@link Specification}.
   * </p>
   *
   * @param query the query object containing optional filters: name, type, and application ID
   * @return a {@link ResponseEntity} containing a list of {@link MenuEntryDTO} that match the filters
   */
  @IgrpQueryHandler
  public ResponseEntity<List<MenuEntryDTO>> handle(GetMenusQuery query) {
    Specification<MenuEntryEntity> spec = buildMenuEntrySpecification(query.getName(), query.getType(), query.getStatus(), query.getApplicationCode());
    List<MenuEntryEntity> menus =  menuEntryRepository.findAll(spec);
    List<MenuEntryDTO> menuEntryDTOs = menus.stream()
            .map(menuEntryMapper::toDTO)
            .toList();

    logger.info("Found {} menu(s) matching the filters.", menus.size());
    return ResponseEntity.ok(menuEntryDTOs);
  }

  private Specification<MenuEntryEntity> buildMenuEntrySpecification(String name, String type, String status, String applicationCode) {
    Specification<MenuEntryEntity> spec = Specification.anyOf();
    if (name != null && !name.isEmpty()) {
      spec = spec.and((root, _, cb) ->
              cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
      );
    }
    if (type != null) {
      MenuEntryType menuEntryType = resolveMenuEntryType(type);
      spec = spec.and((root, _, cb) ->
              cb.equal(root.get("type"), menuEntryType)
      );
    }
    if (status != null) {
      Status menuEntryStatus = resolveMenuEntryStatus(status);
      spec = spec.and((root, _, cb) ->
              cb.equal(root.get("status"), menuEntryStatus)
      );
    }

    if (applicationCode != null) {

      spec = spec.and((root, _, cb) -> {
                Join<MenuEntryEntity, ApplicationEntity> applicationJoin = root.join("applicationId");
                return cb.equal(applicationJoin.get("code"), applicationCode);
              }
      );
    }

    // Exclude deleted menus
    spec = spec.and((root, _, cb) ->
            cb.notEqual(root.get("status"), Status.DELETED)
    );

    return spec;
  }

  /**
   * Resolves the string type to a {@link MenuEntryType}.
   * <p>Throws {@link IgrpResponseStatusException} if the type is invalid.</p>
   *
   * @param type the raw string representation of the enum value
   * @return the resolved {@link MenuEntryType}
   * @throws IgrpResponseStatusException if the type is invalid
   */
  private MenuEntryType resolveMenuEntryType(String type) {
      return MenuEntryType.fromCodeOrThrow(type);
  }

  /**
   * Resolves the string type to a {@link Status}.
   * <p>Throws {@link IgrpResponseStatusException} if the type is invalid.</p>
   *
   * @param status the raw string representation of the enum value
   * @return the resolved {@link Status}
   * @throws IgrpResponseStatusException if the type is invalid
   */
  private Status resolveMenuEntryStatus(String status) {
    return Status.fromCodeOrThrow(status);
  }

}