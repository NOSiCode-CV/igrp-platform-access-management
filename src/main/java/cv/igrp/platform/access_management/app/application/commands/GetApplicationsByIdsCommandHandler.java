package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;

/**
 * Query handler responsible for retrieving a list of {@link ApplicationDTO}s by a list of application IDs.
 *
 * <p>
 * This handler receives a {@link GetApplicationsByIdsCommand}, fetches the corresponding {@link ApplicationEntity}
 * entities from the database, and maps them to their DTO representations using {@link ApplicationMapper}.
 * </p>
 *
 * <p>
 * The result is returned in a {@link ResponseEntity} with HTTP status {@code 200 OK}.
 * </p>
 *
 * @see GetApplicationsByIdsCommand
 * @see ApplicationEntity
 * @see ApplicationDTO
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 */
@Component
public class GetApplicationsByIdsCommandHandler implements CommandHandler<GetApplicationsByIdsCommand, ResponseEntity<List<ApplicationDTO>>> {

   private final ApplicationEntityRepository applicationRepository;
   private final ApplicationMapper applicationMapper;

   /**
    * Constructs the handler with the required dependencies.
    *
    * @param applicationRepository repository to fetch {@link ApplicationEntity} entities
    * @param applicationMapper mapper used to convert {@link ApplicationEntity} to {@link ApplicationDTO}
    */
   public GetApplicationsByIdsCommandHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
   }

   /**
    * Handles the query by retrieving applications for the provided list of IDs.
    *
    * @param query the command containing the list of application IDs to fetch
    * @return a {@link ResponseEntity} containing the list of corresponding {@link ApplicationDTO}s
    */
   @IgrpQueryHandler
   public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsByIdsCommand query) {
      List<ApplicationEntity> applications = applicationRepository.findAllById(query.getGetApplicationsByIdsRequest());
      List<ApplicationDTO> applicationDTOs = applications.stream()
              .map(applicationMapper::toDto)
              .toList();
      return ResponseEntity.ok(applicationDTOs);
   }

}