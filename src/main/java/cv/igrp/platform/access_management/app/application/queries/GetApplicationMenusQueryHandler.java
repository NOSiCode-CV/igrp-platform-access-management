package cv.igrp.platform.access_management.app.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.app.specs.MenuSpecificationBuilder;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.security.ScopeContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetApplicationMenusQueryHandler implements QueryHandler<GetApplicationMenusQuery, ResponseEntity<List<MenuEntryDTO>>> {

    private final MenuEntryEntityRepository menuEntryRepository;
    private final MenuEntryMapper menuEntryMapper;
    private final MenuSpecificationBuilder specification;

    public GetApplicationMenusQueryHandler(MenuEntryEntityRepository menuEntryRepository, MenuEntryMapper menuEntryMapper, MenuSpecificationBuilder specification
    ) {
        this.menuEntryRepository = menuEntryRepository;
        this.menuEntryMapper = menuEntryMapper;
        this.specification = specification;
    }

    @IgrpQueryHandler
    public ResponseEntity<List<MenuEntryDTO>> handle(GetApplicationMenusQuery query) {

        var specs = specification.buildSpecification(query, new ScopeContext());

        var menus = menuEntryRepository.findAll(specs).stream()
                .map(menuEntryMapper::toDTO)
                .toList();

        return ResponseEntity.ok(menus);
    }

}