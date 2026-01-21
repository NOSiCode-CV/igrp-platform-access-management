package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
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
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetDepartmentMenusQueryHandler implements QueryHandler<GetDepartmentMenusQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GetDepartmentMenusQueryHandler.class);

    private final MenuEntryEntityRepository menuRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final MenuEntryMapper menuEntryMapper;

    public GetDepartmentMenusQueryHandler(
            MenuEntryEntityRepository menuRepository,
            DepartmentEntityRepository departmentRepository,
            MenuEntryMapper menuEntryMapper
    ) {
        this.menuRepository = menuRepository;
        this.departmentRepository = departmentRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    @IgrpQueryHandler
    @Transactional(readOnly = true)
    public ResponseEntity<List<MenuEntryDTO>> handle(GetDepartmentMenusQuery query) {

        LOGGER.info("Getting menus for department: {}", query.getDepartmentCode());

        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(query.getDepartmentCode());

        List<MenuEntryDTO> menus = menuRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), query.getMenuCode())
                .stream()
                .map(menuEntryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(menus);

    }

}