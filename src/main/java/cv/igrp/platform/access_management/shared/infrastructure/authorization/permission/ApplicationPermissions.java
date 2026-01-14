package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class ApplicationPermissions {

    private ApplicationPermissions() {
    }

    @IgrpPermission(name = "igrp.application.create", description = "Permission to create application")
    public static final String IGRP_APPLICATION_CREATE = "igrp.application.create";

    @IgrpPermission(name = "igrp.application.update", description = "Permission to update application")
    public static final String IGRP_APPLICATION_UPDATE = "igrp.application.update";

    @IgrpPermission(name = "igrp.application.delete", description = "Permission to delete application")
    public static final String IGRP_APPLICATION_DELETE = "igrp.application.delete";

    @IgrpPermission(name = "igrp.application.view", description = "Permission to view application")
    public static final String IGRP_APPLICATION_VIEW = "igrp.application.view";

    @IgrpPermission(name = "igrp.application.list", description = "Permission to list applications")
    public static final String IGRP_APPLICATION_LIST = "igrp.application.list";

    @IgrpPermission(name = "igrp.application.manage", description = "Permission to manage application")
    public static final String IGRP_APPLICATION_MANAGE = "igrp.application.manage";

    @IgrpPermission(name = "igrp.application.customize", description = "Permission to customize application")
    public static final String IGRP_APPLICATION_CUSTOMIZE = "igrp.application.customize";

}
