package cv.igrp.platform.access_management.page_access.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantResultDTO;
import cv.igrp.platform.access_management.page_access.domain.service.PageGrantService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class GrantPageCommandHandler
        implements CommandHandler<GrantPageCommand, ResponseEntity<PageGrantResultDTO>> {

    private final PageGrantService grants;

    public GrantPageCommandHandler(PageGrantService grants) {
        this.grants = grants;
    }

    @Override
    @IgrpCommandHandler
    public ResponseEntity<PageGrantResultDTO> handle(GrantPageCommand command) {
        return ResponseEntity.ok(grants.grant(command.departmentCode(), command.roleCode(), command.request()));
    }
}
