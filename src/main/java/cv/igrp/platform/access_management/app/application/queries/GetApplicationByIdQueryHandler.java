package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Handles the retrieval of an {@link ApplicationEntity} by its unique identifier.
 *
 * <p>
 * This query handler is responsible for:
 * <ul>
 *     <li>Fetching the {@link ApplicationEntity} from the {@link ApplicationEntityRepository} using the provided ID.</li>
 *     <li>Throwing an {@link IgrpResponseStatusException} with HTTP 404 if the application is not found.</li>
 *     <li>Mapping the retrieved {@link ApplicationEntity} entity to an {@link ApplicationDTO} using the {@link ApplicationMapper}.</li>
 *     <li>Returning the result wrapped in a {@link ResponseEntity} with status {@link HttpStatus#OK}.</li>
 * </ul>
 *
 * @see GetApplicationByIdQuery
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 * @see IgrpResponseStatusException
 */
@Component
public class GetApplicationByIdQueryHandler implements QueryHandler<GetApplicationByIdQuery, ResponseEntity<ApplicationDTO>>{

  private ApplicationEntityRepository applicationRepository;
  private ApplicationMapper applicationMapper;

  /**
   * Constructs a new {@code GetApplicationByIdQueryHandler} with required dependencies.
   *
   * @param applicationRepository repository to retrieve application entities
   * @param applicationMapper     mapper to convert application entities to DTOs
   */
  public GetApplicationByIdQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
  }

  /**
   * Handles the query to retrieve an application by its ID.
   *
   * @param query the query containing the application ID
   * @return a {@link ResponseEntity} with the application data as an {@link ApplicationDTO}
   * @throws IgrpResponseStatusException if the application is not found
   */
  @IgrpQueryHandler
  public ResponseEntity<ApplicationDTO> handle(GetApplicationByIdQuery query) {
    ApplicationEntity application = applicationRepository.findById(query.getId())
            .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found with id: " + query.getId()));
    return ResponseEntity.ok(applicationMapper.toDto(application));
  }

}