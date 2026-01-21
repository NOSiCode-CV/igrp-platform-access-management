package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.department.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetDepartmentResourcesQueryHandler implements QueryHandler<GetDepartmentResourcesQuery, ResponseEntity<List<ResourceDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentResourcesQueryHandler.class);

    private final ResourceEntityRepository resourceRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final ResourceMapper resourceMapper;

    public GetDepartmentResourcesQueryHandler(
            ResourceEntityRepository resourceRepository,
            DepartmentEntityRepository departmentRepository,
            ResourceMapper resourceMapper
    ) {
        this.resourceRepository = resourceRepository;
        this.departmentRepository = departmentRepository;
        this.resourceMapper = resourceMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<ResourceDTO>> handle(GetDepartmentResourcesQuery query) {

        LOGGER.info("Getting resources for department: {}", query.getCode());

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getCode());

        List<ResourceDTO> resources = resourceRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), query.getResourceName())
                .stream()
                .map(resourceMapper::toDto)
                .toList();

        return ResponseEntity.ok(resources);

    }

}