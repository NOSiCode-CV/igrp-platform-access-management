package cv.igrp.platform.access_management.page_access.application.commands;

import cv.igrp.framework.core.domain.Command;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantRequestDTO;

/** Grant a page to a department's role. */
public record GrantPageCommand(String departmentCode, String roleCode, PageGrantRequestDTO request)
        implements Command {
}
