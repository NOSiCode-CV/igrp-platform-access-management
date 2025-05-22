package cv.igrp.platform.access_management.app.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.app.application.queries.queries.GetApplicationsByUserQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the query to retrieve all {@link Application} entities accessible by a specific user.
 *
 * <p>
 * This query handler:
 * <ul>
 *   <li>Uses the {@link ApplicationRepository} to find distinct applications where the given user is associated via roles and departments.</li>
 *   <li>Maps each {@link Application} entity to an {@link ApplicationDTO} using {@link ApplicationMapper}.</li>
 *   <li>Returns the list of applications in a {@link ResponseEntity} with HTTP status {@code 200 OK}.</li>
 * </ul>
 *
 * <p>
 * The user is matched by both their username and email address.
 * </p>
 *
 * @see GetApplicationsByUserQuery
 * @see ApplicationRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Service
public class GetApplicationsByUserQueryHandler implements QueryHandler<GetApplicationsByUserQuery, ResponseEntity<List<ApplicationDTO>>>{

   private ApplicationRepository applicationRepository;
   private ApplicationMapper applicationMapper;

   /**
    * Constructs the query handler with required dependencies.
    *
    * @param applicationRepository repository used to query applications
    * @param applicationMapper     mapper to convert {@link Application} to {@link ApplicationDTO}
    */
   public GetApplicationsByUserQueryHandler(ApplicationRepository applicationRepository, ApplicationMapper applicationMapper) {
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
      List<Application> applications = applicationRepository
              .findDistinctByDepartments_Roles_Users_UsernameOrDepartments_Roles_Users_Email(query.getUid(), query.getUid());

      List<ApplicationDTO> applicationDTOs = applications.stream()
              .map(applicationMapper::toDto)
              .toList();

      return ResponseEntity.ok(applicationDTOs);
   }

}