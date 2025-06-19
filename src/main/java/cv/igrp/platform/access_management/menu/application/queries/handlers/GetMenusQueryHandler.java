package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpProblem;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import jakarta.persistence.criteria.Join;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenusQuery;
import java.util.List;

/**
 * Query handler responsible for retrieving a list of {@link MenuEntryDTO} objects
 * based on filtering criteria such as menu name, application ID, and type.
 * <p>
 * This handler uses Spring Data JPA's {@link Specification} to dynamically construct
 * queries that match the provided filter parameters. The result is then mapped from
 * {@link MenuEntry} entities to {@link MenuEntryDTO} for safe client exposure.
 * </p>
 *
 */
@Service
public class GetMenusQueryHandler implements QueryHandler<GetMenusQuery, ResponseEntity<List<MenuEntryDTO>>>{

   private static final Logger logger = LoggerFactory.getLogger(GetMenusQueryHandler.class);

   private final MenuEntryRepository menuEntryRepository;
   private final MenuEntryMapper menuEntryMapper;


   /**
    * Constructs a {@code GetMenusQueryHandler} with the required dependencies.
    *
    * @param menuEntryRepository the repository used to retrieve {@link MenuEntry} entities
    * @param menuEntryMapper the mapper used to convert {@link MenuEntry} to {@link MenuEntryDTO}
    */
   public GetMenusQueryHandler(MenuEntryRepository menuEntryRepository, MenuEntryMapper menuEntryMapper) {
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
      Specification<MenuEntry> spec = buildMenuEntrySpecification(query.getName(), query.getApplicationId(), query.getType(), query.getStatus(), query.getApplicationCode());
      List<MenuEntry> menus =  menuEntryRepository.findAll(spec);
      List<MenuEntryDTO> menuEntryDTOs = menus.stream()
              .map(menuEntryMapper::toDTO)
              .toList();

      logger.info("Found {} menu(s) matching the filters.", menus.size());
      return ResponseEntity.ok(menuEntryDTOs);
   }

   private Specification<MenuEntry> buildMenuEntrySpecification(String name, Integer applicationId, String type, String status, String applicationCode) {
      Specification<MenuEntry> spec = Specification.where(null);
      if (name != null && !name.isEmpty()) {
         spec = spec.and((root, query, cb) ->
                 cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
         );
      }
      if (type != null) {
         MenuEntryType menuEntryType = resolveMenuEntryType(type);
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("type"), menuEntryType)
         );
      }
      if (status != null) {
         Status menuEntryStatus = resolveMenuEntryStatus(status);
         spec = spec.and((root, query, cb) ->
                 cb.equal(root.get("status"), menuEntryStatus)
         );
      }
      if (applicationId != null) {

         spec = spec.and((root, query, cb) -> {
               Join<MenuEntry, Application> applicationJoin = root.join("applicationId");
               return cb.equal(applicationJoin.get("id"), applicationId);
            }
         );
      }

      if (applicationCode != null) {

         spec = spec.and((root, query, cb) -> {
               Join<MenuEntry, Application> applicationJoin = root.join("applicationId");
               return cb.equal(applicationJoin.get("code"), applicationCode);
            }
         );
      }

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
      try {
         return MenuEntryType.valueOf(type);
      } catch (IllegalArgumentException ex) {
         logger.warn("Invalid menu type provided: '{}'", type);
         throw new IgrpResponseStatusException(
                 new IgrpProblem<>(HttpStatus.BAD_REQUEST,
                         "Invalid menu type",
                         "No menu type found with name: " + type)
         );
      }
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
      try {
         return Status.valueOf(status);
      } catch (IllegalArgumentException ex) {
         logger.warn("Invalid menu status provided: '{}'", status);
         throw new IgrpResponseStatusException(
                 new IgrpProblem<>(HttpStatus.BAD_REQUEST,
                         "Invalid menu status",
                         "No menu status found with name: " + status)
         );
      }
   }

}
