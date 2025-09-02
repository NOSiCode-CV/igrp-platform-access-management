package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Query handler responsible for retrieving a list of {@link DepartmentEntity} entities
 * based on optional filtering criteria such as application ID, department name, and status.
 * <p>
 * This handler processes {@link GetDepartmentsQuery} requests and performs the following steps:
 * <ul>
 *   <li>Dynamically builds a JPA {@link Specification} based on the provided query filters.</li>
 *   <li>Retrieves the matching {@link DepartmentEntity} entities from the {@link DepartmentEntityRepository}.</li>
 *   <li>Maps the retrieved entities to {@link DepartmentDTO} objects using {@link DepartmentMapper}.</li>
 *   <li>Returns the result list wrapped in an HTTP 200 OK {@link ResponseEntity}.</li>
 * </ul>
 *
 * <p>Logging is included to aid in query debugging and traceability.</p>
 */
@Component
public class GetDepartmentsQueryHandler implements QueryHandler<GetDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>>{

  private static final Logger logger =
          LoggerFactory.getLogger(GetDepartmentsQueryHandler.class);

  private final DepartmentEntityRepository departmentRepository;
  private final DepartmentMapper departmentMapper;

  /**
   * Constructs the query handler with necessary dependencies.
   *
   * @param departmentRepository the repository to retrieve department data
   * @param departmentMapper     the mapper to convert department entities to DTOs
   */
  public GetDepartmentsQueryHandler(
          DepartmentEntityRepository departmentRepository,
          DepartmentMapper departmentMapper) {
    this.departmentRepository = departmentRepository;
    this.departmentMapper = departmentMapper;
  }

  /**
   * Handles a query to retrieve a list of departments filtered by application ID, name, and status.
   *
   * @param query the query object containing optional filter criteria
   * @return a {@link ResponseEntity} with a list of {@link DepartmentDTO}s and HTTP status 200 OK
   */
  @IgrpQueryHandler
  public ResponseEntity<List<DepartmentDTO>> handle(GetDepartmentsQuery query) {
    logger.info("Handling GetDepartmentsQuery: name={}, status={}, parentCode={}",
            query.getName(), query.getStatus(), query.getParentCode());

    Specification<DepartmentEntity> spec = (root, _, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (query.getName() != null && !query.getName().isEmpty()) {
        predicates.add(cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
      }

      if (query.getStatus() != null) {
        DepartmentStatus departmentStatus = resolveDepartmentStatus(query.getStatus());
        predicates.add(cb.equal(root.get("status"), departmentStatus));
      }

      if (query.getCode() != null) {
        predicates.add(cb.equal(root.get("code"), query.getCode()));
      }

      if (query.getParentCode() != null) {
        Join<DepartmentEntity, DepartmentEntity> parentJoin = root.join("parentId");
        predicates.add(cb.equal(parentJoin.get("code"), query.getParentCode()));
      }

      // Exclude deleted departments
      predicates.add(cb.notEqual(root.get("status"), DepartmentStatus.DELETED));

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    List<DepartmentEntity> departments = departmentRepository.findAll(spec);
    List<DepartmentDTO> dtos = departments.stream()
            .map(departmentMapper::toDto)
            .toList();

    return ResponseEntity.ok(dtos);
  }

  private DepartmentStatus resolveDepartmentStatus(String status) {
    try {
      return DepartmentStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      logger.warn("Invalid status provided: '{}'", status);
      throw IgrpResponseStatusException.of(
              HttpStatus.BAD_REQUEST,
              "Invalid department status",
              "No department status found with name: " + status
      );
    }
  }

}