package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;

@Component
public class GetAvailableApplicationsForDepartmentQueryHandler implements QueryHandler<GetAvailableApplicationsForDepartmentQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetAvailableApplicationsForDepartmentQueryHandler.class);

    private final ApplicationEntityRepository applicationEntityRepository;
    private final DepartmentEntityRepository departmentEntityRepository;
    private final ApplicationMapper applicationMapper;


    public GetAvailableApplicationsForDepartmentQueryHandler(ApplicationEntityRepository applicationEntityRepository, DepartmentEntityRepository departmentEntityRepository, ApplicationMapper applicationMapper) {
        this.departmentEntityRepository = departmentEntityRepository;
        this.applicationEntityRepository = applicationEntityRepository;
        this.applicationMapper = applicationMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<ApplicationDTO>> handle(GetAvailableApplicationsForDepartmentQuery query) {
        LOGGER.info("Getting Available Applications For Department for department: {}", query.getCode());

        // Verify if the department exists
        departmentEntityRepository.findByCodeAndStatusNotDeleted(query.getCode());

        List<ApplicationDTO> availableApplications = applicationEntityRepository.findAvailableApplicationsForDepartment(query.getCode())
                .stream()
                .map(applicationMapper::toDto)
                .toList();
        return ResponseEntity.ok(availableApplications);
    }

}