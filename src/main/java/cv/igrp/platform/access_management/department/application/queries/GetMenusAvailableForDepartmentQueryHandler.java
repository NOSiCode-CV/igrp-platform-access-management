package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;

@Component
public class GetMenusAvailableForDepartmentQueryHandler implements QueryHandler<GetMenusAvailableForDepartmentQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetMenusAvailableForDepartmentQueryHandler.class);

    private final MenuEntryEntityRepository menuEntryEntityRepository;
    private final DepartmentEntityRepository departmentEntityRepository;
    private final ApplicationEntityRepository applicationEntityRepository;
    private final MenuEntryMapper menuEntryMapper;

    public GetMenusAvailableForDepartmentQueryHandler(
            MenuEntryEntityRepository menuEntryEntityRepository,
            DepartmentEntityRepository departmentEntityRepository,
            ApplicationEntityRepository applicationEntityRepository,
            MenuEntryMapper menuEntryMapper
    ) {
        this.departmentEntityRepository = departmentEntityRepository;
        this.menuEntryEntityRepository = menuEntryEntityRepository;
        this.applicationEntityRepository = applicationEntityRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<MenuEntryDTO>> handle(GetMenusAvailableForDepartmentQuery query) {
        LOGGER.info("Getting Menus Available for department: {}", query.getDepartmentCode());

        // Verify if the department exists
        departmentEntityRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());
        // Verify if the application exists
        ApplicationEntity application = applicationEntityRepository.findByCodeAndStatusNotDeleted(query.getApplicationCode());

        List<MenuEntryDTO> menus = menuEntryEntityRepository.findAvailableMenusForDepartment(query.getDepartmentCode(), application)
                .stream()
                .map(menuEntryMapper::toDTO)
                .toList();

        LOGGER.info("Found {} menus for department: {}", menus.size(), query.getDepartmentCode());

        return ResponseEntity.ok(menus);
    }

}