package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.department.specs.DepartmentSpecificationBuilder;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.spring.Scoped;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import jakarta.persistence.criteria.*;
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

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_DEPARTMENT;

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
public class GetDepartmentsQueryHandler implements QueryHandler<GetDepartmentsQuery, ResponseEntity<List<DepartmentDTO>>> {

    private static final Logger logger =
            LoggerFactory.getLogger(GetDepartmentsQueryHandler.class);

    private final DepartmentEntityRepository departmentRepository;
    private final DepartmentMapper departmentMapper;
    private final DepartmentSpecificationBuilder specification;

    /**
     * Constructs the query handler with necessary dependencies.
     *
     * @param departmentRepository the repository to retrieve department data
     * @param departmentMapper     the mapper to convert department entities to DTOs
     * @param specification the builder to create JPA specifications for department filtering
     */
    public GetDepartmentsQueryHandler(
            DepartmentEntityRepository departmentRepository,
            DepartmentMapper departmentMapper,
            DepartmentSpecificationBuilder specification
    ) {
        this.departmentRepository = departmentRepository;
        this.departmentMapper = departmentMapper;
        this.specification = specification;
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

        var spec = specification.buildSpecification(query, new ScopeContext());

        List<DepartmentEntity> departments = departmentRepository.findAll(spec);
        List<DepartmentDTO> dtos = departments.stream()
                .map(departmentMapper::toDto)
                .toList();

        return ResponseEntity.ok(dtos);
    }

}