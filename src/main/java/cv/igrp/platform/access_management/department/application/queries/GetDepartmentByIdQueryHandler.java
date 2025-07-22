package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;

/**
 * Query handler responsible for retrieving a {@link DepartmentEntity} by its identifier and returning it as a {@link DepartmentDTO}.
 * <p>
 * This handler processes {@link GetDepartmentByIdQuery} queries and performs the following:
 * <ul>
 *   <li>Looks up the department using the provided ID.</li>
 *   <li>Throws {@link IgrpResponseStatusException} if no department is found.</li>
 *   <li>Converts the domain entity to a DTO using {@link DepartmentMapper}.</li>
 *   <li>Returns the result in a {@link ResponseEntity} with HTTP 200 OK.</li>
 * </ul>
 *
 */
@Component
public class GetDepartmentByIdQueryHandler implements QueryHandler<GetDepartmentByIdQuery, ResponseEntity<DepartmentDTO>>{

  private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentByIdQueryHandler.class);

  private final DepartmentEntityRepository departmentRepository;
  private final DepartmentMapper departmentMapper;

  /**
   * Constructs the handler with required dependencies.
   *
   * @param departmentRepository the repository for accessing department data
   * @param departmentMapper the mapper for converting entities to DTOs
   */
  public GetDepartmentByIdQueryHandler(
          DepartmentEntityRepository departmentRepository,
          DepartmentMapper departmentMapper) {
    this.departmentRepository = departmentRepository;
    this.departmentMapper = departmentMapper;
  }

  /**
   * Handles the retrieval of a department by ID.
   *
   * @param query the query containing the department ID
   * @return a {@link ResponseEntity} with HTTP 200 and the mapped {@link DepartmentDTO}
   * @throws IgrpResponseStatusException if the department is not found
   */
  @IgrpQueryHandler
  public ResponseEntity<DepartmentDTO> handle(GetDepartmentByIdQuery query) {
    Integer departmentId = query.getId();
    LOGGER.info("Fetching department with id={}", departmentId);

    DepartmentEntity department = departmentRepository.findById(departmentId)
            .orElseThrow(() -> {
              LOGGER.warn("Department with id={} not found", departmentId);
              return IgrpResponseStatusException.of(
                      HttpStatus.NOT_FOUND, "Invalid Department ID", "Department not found with id: " + departmentId);
            });

    DepartmentDTO dto = departmentMapper.toDto(department);
    LOGGER.info("Successfully retrieved department id={} name={}", dto.getId(), dto.getName());

    return ResponseEntity.ok(dto);
  }

}