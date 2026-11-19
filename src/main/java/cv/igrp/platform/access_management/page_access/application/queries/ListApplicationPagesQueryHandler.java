package cv.igrp.platform.access_management.page_access.application.queries;

import cv.igrp.framework.core.domain.QueryHandler;
import cv.igrp.framework.stereotype.IgrpQueryHandler;
import cv.igrp.platform.access_management.page_access.application.dto.ApplicationPageDTO;
import cv.igrp.platform.access_management.page_access.domain.service.PageManifestService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ListApplicationPagesQueryHandler
        implements QueryHandler<ListApplicationPagesQuery, ResponseEntity<List<ApplicationPageDTO>>> {

    private final PageManifestService manifests;

    public ListApplicationPagesQueryHandler(PageManifestService manifests) {
        this.manifests = manifests;
    }

    @Override
    @IgrpQueryHandler
    public ResponseEntity<List<ApplicationPageDTO>> handle(ListApplicationPagesQuery query) {
        return ResponseEntity.ok(manifests.pages(query.applicationCode()));
    }
}
