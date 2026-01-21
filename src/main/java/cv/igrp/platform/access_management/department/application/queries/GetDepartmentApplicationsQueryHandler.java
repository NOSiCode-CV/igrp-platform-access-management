package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetDepartmentApplicationsQueryHandler implements QueryHandler<GetDepartmentApplicationsQuery, ResponseEntity<List<ApplicationDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentApplicationsQueryHandler.class);

    private final ApplicationEntityRepository applicationRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final ApplicationMapper applicationMapper;

    public GetDepartmentApplicationsQueryHandler(ApplicationEntityRepository applicationRepository, DepartmentEntityRepository departmentRepository, ApplicationMapper applicationMapper) {
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
        this.applicationMapper = applicationMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<ApplicationDTO>> handle(GetDepartmentApplicationsQuery query) {

        LOGGER.info("Getting applications for department: {}", query.getCode());

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getCode());

        List<ApplicationDTO> applications = applicationRepository.findByDepartmentAndStatusNotFiltered(department, Status.DELETED, query.getApplicationCode())
                        .stream()
                        .map(applicationMapper::toDto)
                        .collect(Collectors.toList());

        return ResponseEntity.ok(applications);

    }

}