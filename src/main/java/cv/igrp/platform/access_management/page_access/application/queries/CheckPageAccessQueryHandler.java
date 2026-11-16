package cv.igrp.platform.access_management.page_access.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.page_access.application.dto.PageAccessDecisionDTO;
import cv.igrp.platform.access_management.page_access.domain.service.PageAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class CheckPageAccessQueryHandler
        implements QueryHandler<CheckPageAccessQuery, ResponseEntity<PageAccessDecisionDTO>> {

    private final PageAccessService pages;

    public CheckPageAccessQueryHandler(PageAccessService pages) {
        this.pages = pages;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<PageAccessDecisionDTO> handle(CheckPageAccessQuery query) {
        return ResponseEntity.ok(pages.check(query.applicationCode(), query.path()));
    }
}
