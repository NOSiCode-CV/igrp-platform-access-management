package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;



@Component
public class GetApplicationByCodeQueryHandler implements QueryHandler<GetApplicationByCodeQuery, ResponseEntity<ApplicationDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetApplicationByCodeQueryHandler.class);
  private final ApplicationEntityRepository applicationRepository;
  private final ApplicationMapper applicationMapper;

    /**
     * Constructs a new {@code GetApplicationByIdQueryHandler} with required dependencies.
     *
     * @param applicationRepository repository to retrieve application entities
     * @param applicationMapper     mapper to convert application entities to DTOs
     */
    public GetApplicationByCodeQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
      this.applicationRepository = applicationRepository;
      this.applicationMapper = applicationMapper;
    }

    /**
     * Handles the query to retrieve an application by its CODE.
     *
     * @param query the query containing the application CODE
     * @return a {@link ResponseEntity} with the application data as an {@link ApplicationDTO}
     * @throws IgrpResponseStatusException if the application is not found
     */


   @IgrpQueryHandler
  public ResponseEntity<ApplicationDTO> handle(GetApplicationByCodeQuery query) {
    ApplicationEntity application = applicationRepository.findByCodeAndStatusNot(query.getCode(), Status.DELETED)
            .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, "Application not found", "Application not found with code: " + query.getCode()));
    return ResponseEntity.ok(applicationMapper.toDto(application));
  }

}