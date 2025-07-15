package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
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

  private ApplicationEntityRepository applicationRepository;
  private ApplicationMapper applicationMapper;

  /**
   * Constructs the handler with required dependencies.
   *
   * @param applicationRepository the repository used to fetch applications
   * @param applicationMapper     mapper to convert {@link ApplicationEntity} entities to {@link ApplicationDTO}
   */
  public GetApplicationsQueryHandler(ApplicationEntityRepository applicationRepository, ApplicationMapper applicationMapper) {
    this.applicationRepository = applicationRepository;
    this.applicationMapper = applicationMapper;
  }

  /**
   * Handles the {@link GetApplicationsQuery}, building a dynamic specification to filter by code and/or name.
   *
   * @param applicationsQuery the query object containing optional filter parameters
   * @return a {@link ResponseEntity} with the list of filtered {@link ApplicationDTO}s
   */
  @IgrpQueryHandler
  public ResponseEntity<List<ApplicationDTO>> handle(GetApplicationsQuery applicationsQuery) {
    Specification<ApplicationEntity> spec = buildSpecification(applicationsQuery.getCode(), applicationsQuery.getName(), applicationsQuery.getSlug());
    List<ApplicationDTO> applications = applicationRepository.findAll(spec)
            .stream()
            .map(applicationMapper::toDto)
            .toList();
    return ResponseEntity.ok(applications);
  }

  /**
   * Builds a dynamic JPA {@link Specification} based on optional code and name filters.
   *
   * @param code the exact code to match (optional)
   * @param name the name substring to search for, case-insensitive (optional)
   * @return a {@link Specification} representing the composed query filters
   */
  private Specification<ApplicationEntity> buildSpecification(final String code, final String name, final String slug) {
    Specification<ApplicationEntity> spec = Specification.where(null);
    if (code != null && !code.isEmpty()) {
      spec = spec.and((root, query, cb) ->
              cb.equal(root.get("code"), code)
      );
    }
    if (name != null && !name.isEmpty()) {
      spec = spec.and((root, query, cb) ->
              cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%")
      );
    }
    if (slug != null && !slug.isEmpty()) {
      spec = spec.and((root, query, cb) ->
              cb.equal(cb.lower(root.get("slug")), slug.toLowerCase())
      );
    }

    return spec;
  }

}