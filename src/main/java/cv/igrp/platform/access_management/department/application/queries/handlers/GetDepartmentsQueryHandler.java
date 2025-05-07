package cv.igrp.platform.access_management.department.application.queries.handlers;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import cv.igrp.platform.access_management.department.application.queries.queries.GetDepartmentsQuery;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;

/**
 * Query handler responsible for retrieving a list of {@link Department} entities
 * based on optional filtering criteria such as application ID, department name, and status.
 * <p>
 * This handler processes {@link GetDepartmentsQuery} requests and performs the following steps:
 * <ul>
 *   <li>Dynamically builds a JPA {@link Specification} based on the provided query filters.</li>
 *   <li>Retrieves the matching {@link Department} entities from the {@link DepartmentRepository}.</li>
 *   <li>Maps the retrieved entities to {@link DepartmentDTO} objects using {@link DepartmentMapper}.</li>
 *   <li>Returns the result list wrapped in an HTTP 200 OK {@link ResponseEntity}.</li>
 * </ul>
 *
 * <p>Logging is included to aid in query debugging and traceability.</p>
 *
 */
@Service
public class GetDepartmentsQueryHandler implements QueryHandler<GetDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>> {
    private static final Logger logger = LoggerFactory.getLogger(GetDepartmentsQueryHandler.class);
    private final DepartmentRepository departmentRepository;
    private final DepartmentMapper departmentMapper;

    /**
     * Constructs the query handler with necessary dependencies.
     *
     * @param departmentRepository the repository to retrieve department data
     * @param departmentMapper the mapper to convert department entities to DTOs
     */
    public GetDepartmentsQueryHandler(DepartmentRepository departmentRepository, DepartmentMapper departmentMapper) {
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
    logger.info("Handling GetDepartmentsQuery: applicationId={}, name={}, status={}",
            query.getApplicationId(), query.getName(), query.getStatus());

    Specification<Department> spec = (root, q, cb) -> {
        List<Predicate> predicates = new ArrayList<>();

        if (query.getApplicationId() != null) {
            predicates.add(cb.equal(root.get("application").get("id"), query.getApplicationId()));
        }

        if (query.getName() != null && !query.getName().isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get("name")), "%" + query.getName().toLowerCase() + "%"));
        }

        if (query.getStatus() != null) {
            predicates.add(cb.equal(root.get("status"), query.getStatus()));
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    };

    List<Department> departments = departmentRepository.findAll(spec);
    List<DepartmentDTO> dtos = departments.stream()
        .map(departmentMapper::toDto)
        .toList();

    return ResponseEntity.ok(dtos);
}
}