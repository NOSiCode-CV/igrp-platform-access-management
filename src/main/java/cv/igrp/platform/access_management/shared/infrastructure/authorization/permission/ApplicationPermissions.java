package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class ApplicationPermissions {

    private ApplicationPermissions() {
    }

    @IgrpPermission(name = "igrp.applications.create", description = "Permission to create application")
    public static final String IGRP_APPLICATIONS_CREATE = "igrp.applications.create";

    @IgrpPermission(name = "igrp.applications.update", description = "Permission to update application")
    public static final String IGRP_APPLICATIONS_UPDATE = "igrp.applications.update";

    @IgrpPermission(name = "igrp.applications.delete", description = "Permission to delete application")
    public static final String IGRP_APPLICATIONS_DELETE = "igrp.applications.delete";

    @IgrpPermission(name = "igrp.applications.view", description = "Permission to view application")
    public static final String IGRP_APPLICATIONS_VIEW = "igrp.applications.view";

    @IgrpPermission(name = "igrp.applications.list", description = "Permission to list applications")
    public static final String IGRP_APPLICATIONS_LIST = "igrp.applications.list";

    @IgrpPermission(name = "igrp.applications.manage", description = "Permission to manage application")
    public static final String IGRP_APPLICATIONS_MANAGE = "igrp.applications.manage";

    @IgrpPermission(name = "igrp.applications.customize", description = "Permission to customize application")
    public static final String IGRP_APPLICATIONS_CUSTOMIZE = "igrp.applications.customize";

}
