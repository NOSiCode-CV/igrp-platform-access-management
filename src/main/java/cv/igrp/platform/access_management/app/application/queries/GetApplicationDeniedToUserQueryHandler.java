package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Handles the query to retrieve applications that are *not* accessible to a specific user.
 *
 * <p>
 * This query handler:
 * <ul>
 *   <li>Uses the {@link ApplicationEntityRepository} to fetch applications not assigned to the user via roles and departments.</li>
 *   <li>Maps the resulting {@link ApplicationEntity} entities to {@link ApplicationDTO} objects using {@link ApplicationMapper}.</li>
 *   <li>Returns the list of denied applications in a {@link ResponseEntity} with status {@code 200 OK}.</li>
 * </ul>
 *
 * <p>
 * This can be used, for example, to populate a list of applications the user cannot currently access.
 * </p>
 *
 * @see GetApplicationDeniedToUserQuery
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Component
public class GetApplicationDeniedToUserQueryHandler implements QueryHandler<GetApplicationDeniedToUserQuery, ResponseEntity<List<ApplicationDTO>>>{

  private final ApplicationEntityRepository applicationRepository;
  private final ApplicationMapper applicationMapper;

  /**
   * Constructs the query handler with the required dependencies.
   *
   * @param applicationRepository repository used to fetch denied applications
   * @param applicationMapper mapper used to convert entities to DTOs
   */
  public GetApplicationDeniedToUserQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
  }

  /**
   * Handles the query to fetch applications the user is denied access to.
   *
   * @param query the query containing the user identifier
   * @return a {@link ResponseEntity} containing the list of denied applications as {@link ApplicationDTO}s
   */
  @IgrpQueryHandler
  public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationDeniedToUserQuery query) {
    List<ApplicationDTO> deniedApplications = applicationRepository.findDeniedApplications(query.getUid()).stream()
            .map(applicationMapper::toDto)
            .toList();
    return ResponseEntity.ok(deniedApplications);
  }

}