package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.app.specs.ApplicationSpecificationBuilder;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Handles the retrieval of {@link ApplicationEntity} entities filtered by optional code and/or name.
 *
 * <p>
 * This query handler processes a {@link GetApplicationsQuery} request and builds a dynamic {@link Specification}
 * to filter applications based on:
 * <ul>
 *   <li>{@code code} - exact match</li>
 *   <li>{@code name} - case-insensitive substring match</li>
 * </ul>
 *
 * <p>
 * The resulting applications are mapped to {@link ApplicationDTO}s and returned in a {@link ResponseEntity}
 * with status {@code 200 OK}.
 * </p>
 *
 * @see GetApplicationsQuery
 * @see ApplicationEntityRepository
 * @see ApplicationMapper
 * @see ApplicationDTO
 */
@Component
public class GetApplicationsQueryHandler implements QueryHandler<GetApplicationsQuery, ResponseEntity<List<ApplicationDTO>>>{

  private final ApplicationEntityRepository applicationRepository;
  private final ApplicationMapper applicationMapper;
  private final ApplicationSpecificationBuilder specification;

  /**
   * Constructs the handler with required dependencies.
   *
   * @param applicationRepository the repository used to fetch applications
   * @param applicationMapper     mapper to convert {@link ApplicationEntity} entities to {@link ApplicationDTO}
   * @param specification         builder to create dynamic specifications for applications
   */
  public GetApplicationsQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper, ApplicationSpecificationBuilder specification) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
    this.specification = specification;
  }

  /**
   * Handles the {@link GetApplicationsQuery}, building a dynamic specification to filter by code and/or name.
   *
   * @param applicationsQuery the query object containing optional filter parameters
   * @return a {@link ResponseEntity} with the list of filtered {@link ApplicationDTO}s
   */
  @IgrpQueryHandler
  public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsQuery applicationsQuery) {
    Specification<ApplicationEntity> spec = specification.buildSpecification(applicationsQuery, new ScopeContext());
    List<ApplicationDTO> applications = applicationRepository.findAll(spec)
            .stream()
            .map(applicationMapper::toDto)
            .toList();
    return ResponseEntity.ok(applications);
  }

}