package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetApplicationMenusQueryHandler implements QueryHandler<GetApplicationMenusQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private final ApplicationEntityRepository applicationRepository;
    private final MenuEntryEntityRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;

    public GetApplicationMenusQueryHandler(ApplicationEntityRepository applicationRepository, MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper
    ) {
        this.applicationRepository = applicationRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.menuEntryMapper = menuEntryMapper;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<MenuEntryDTO>> handle(GetApplicationMenusQuery query) {

        var app = applicationRepository.findByCodeAndStatusNotDeleted(query.getCode());

        var menus = menuEntryRepository.findByApplicationIdAndStatus(app, Status.ACTIVE)
                .stream()
                .map(menuEntryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(menus);
    }

}