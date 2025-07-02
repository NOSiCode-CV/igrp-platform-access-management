package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentByIdQuery;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;

/**
 * Query handler responsible for retrieving a {@link Department} by its identifier and returning it as a {@link DepartmentDTO}.
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
@Service
public class GetDepartmentByIdQueryHandler implements QueryHandler<GetDepartmentByIdQuery, ResponseEntity<DepartmentDTO>> {

    private static final Logger logger =
            LoggerFactory.getLogger(GetDepartmentByIdQueryHandler.class);

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param departmentRepository the repository for accessing department data
     * @param departmentMapper the mapper for converting entities to DTOs
     */
    public GetDepartmentByIdQueryHandler(
            DepartmentRepository departmentRepository,
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
        logger.info("Fetching department with id={}", departmentId);

        Department department = departmentRepository.findById(departmentId)
            .orElseThrow(() -> {
                logger.warn("Department with id={} not found", departmentId);
                return IgrpResponseStatusException.of(
                        HttpStatus.NOT_FOUND, "Invalid Department ID", "Department not found with id: " + departmentId);
            });

        DepartmentDTO dto = departmentMapper.toDto(department);
        logger.info("Successfully retrieved department id={} name={}", dto.getId(), dto.getName());

        return ResponseEntity.ok(dto);
    }
}
