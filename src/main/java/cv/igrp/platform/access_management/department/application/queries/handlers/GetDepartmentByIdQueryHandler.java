package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentByIdQuery;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;

/**
 * Query handler responsible for retrieving a {@link Department} by its identifier and returning it as a {@link DepartmentDTO}.
 * <p>
 * This handler processes {@link GetDepartmentByIdQuery} queries and performs the following:
 * <ul>
 *   <li>Looks up the department using the provided ID.</li>
 *   <li>Throws {@link EntityNotFoundException} if no department is found.</li>
 *   <li>Converts the domain entity to a DTO using {@link DepartmentMapper}.</li>
 *   <li>Returns the result in a {@link ResponseEntity} with HTTP 200 OK.</li>
 * </ul>
 *
 */
@Service
public class GetDepartmentByIdQueryHandler implements QueryHandler<GetDepartmentByIdQuery, ResponseEntity<DepartmentDTO>> {

    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    /**
     * Constructs the handler with required dependencies.
     *
     * @param departmentRepository the repository for accessing department data
     * @param departmentMapper the mapper for converting entities to DTOs
     */
    public GetDepartmentByIdQueryHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
    }

    /**
     * Handles the retrieval of a department by ID.
     *
     * @param query the query containing the department ID
     * @return a {@link ResponseEntity} with HTTP 200 and the mapped {@link DepartmentDTO}
     * @throws EntityNotFoundException if the department is not found
     */
    @IgrpQueryHandler
    public ResponseEntity<DepartmentDTO> handle(GetDepartmentByIdQuery query) {
        Department department = departmentRepository.findById(query.getId())
            .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + query.getId()));

        DepartmentDTO dto = departmentMapper.toDto(department);
        return ResponseEntity.ok(dto);
    }
}
