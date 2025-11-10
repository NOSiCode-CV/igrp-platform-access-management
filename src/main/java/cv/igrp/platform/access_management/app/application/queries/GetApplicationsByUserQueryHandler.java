package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Handles the query to retrieve all {@link ApplicationEntity} entities accessible by a specific user.
 *
 * <p>
 * This query handler:
 * <ul>
 *   <li>Uses the {@link ApplicationEntityRepository} to find distinct applications where the given user is associated via roles and departments.</li>
 *   <li>Maps each {@link ApplicationEntity} entity to an {@link ApplicationDTO} using {@link ApplicationMapper}.</li>
 *   <li>Returns the list of applications in a {@link ResponseEntity} with HTTP status {@code 200 OK}.</li>
 * </ul>
 *
 * <p>
 * The user is matched by both their username and email address.
 * </p>
 *
 * @see GetApplicationsByUserQuery
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Component
public class GetApplicationsByUserQueryHandler implements QueryHandler<GetApplicationsByUserQuery, ResponseEntity<List<ApplicationDTO>>>{

  private final ApplicationEntityRepository applicationRepository;
  private final ApplicationMapper applicationMapper;

  /**
   * Constructs the query handler with required dependencies.
   *
   * @param applicationRepository repository used to query applications
   * @param applicationMapper     mapper to convert {@link ApplicationEntity} to {@link ApplicationDTO}
   */
  public GetApplicationsByUserQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
  }

  /**
   * Handles the incoming {@link GetApplicationsByUserQuery} by retrieving applications
   * associated with the given user (matched by username or email).
   *
   * @param query the query containing the user identifier
   * @return a {@link ResponseEntity} containing a list of {@link ApplicationDTO}
   */
  @IgrpQueryHandler
  public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsByUserQuery query) {

      List<ApplicationEntity> applications = applicationRepository
            .findApplicationsByUserOrEmailAndStatus(query.getUid(), query.getUid(), Status.ACTIVE);

    List<ApplicationDTO> applicationDTOs = applications.stream()
            .map(applicationMapper::toDto)
            .toList();

    return ResponseEntity.ok(applicationDTOs);
  }

}