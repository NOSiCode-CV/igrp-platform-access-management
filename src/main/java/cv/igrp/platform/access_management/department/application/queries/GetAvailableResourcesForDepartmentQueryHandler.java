package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_RESOURCE;

@Component
public class GetAvailableResourcesForDepartmentQueryHandler implements QueryHandler<GetAvailableResourcesForDepartmentQuery, ResponseEntity<List<ResourceDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailableResourcesForDepartmentQueryHandler.class);

    private final ResourceMapper resourceMapper;
    private final ResourceEntityRepository resourceEntityRepository;
    private final DepartmentEntityRepository departmentEntityRepository;

    public GetAvailableResourcesForDepartmentQueryHandler(ResourceMapper resourceMapper, ResourceEntityRepository resourceEntityRepository, DepartmentEntityRepository departmentEntityRepository) {
        this.departmentEntityRepository = departmentEntityRepository;
        this.resourceMapper = resourceMapper;
        this.resourceEntityRepository = resourceEntityRepository;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<ResourceDTO>> handle(GetAvailableResourcesForDepartmentQuery query) {
        LOGGER.info("Getting Available Resources for department: {}", query.getCode());

        // Verify if the department exists
        departmentEntityRepository.findByCodeAndStatusNotDeleted(query.getCode());

        List<ResourceDTO> availableResources = resourceEntityRepository.findAvailableResourcesForDepartment(query.getCode(), IGRP_RESOURCE)
                .stream()
                .map(resourceMapper::toDto)
                .toList();

        LOGGER.info("Found {} available resources for department: {}", availableResources.size(), query.getCode());

        return ResponseEntity.ok(availableResources);
    }

}