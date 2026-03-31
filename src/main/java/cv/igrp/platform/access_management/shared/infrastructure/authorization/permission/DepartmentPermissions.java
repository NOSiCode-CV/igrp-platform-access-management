package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class DepartmentPermissions {

    private DepartmentPermissions() {
    }

    @IgrpPermission(name = "igrp.departments.create", description = "Permission to create department")
    public static final String IGRP_DEPARTMENTS_CREATE = "igrp.departments.create";

    @IgrpPermission(name = "igrp.departments.update", description = "Permission to update department")
    public static final String IGRP_DEPARTMENTS_UPDATE = "igrp.departments.update";

    @IgrpPermission(name = "igrp.departments.delete", description = "Permission to delete department")
    public static final String IGRP_DEPARTMENTS_DELETE = "igrp.departments.delete";

    @IgrpPermission(name = "igrp.departments.view", description = "Permission to view department")
    public static final String IGRP_DEPARTMENTS_VIEW = "igrp.departments.view";

    @IgrpPermission(name = "igrp.departments.list", description = "Permission to list departments")
    public static final String IGRP_DEPARTMENTS_LIST = "igrp.departments.list";

    @IgrpPermission(name = "igrp.departments.manage", description = "Permission to manage department")
    public static final String IGRP_DEPARTMENTS_MANAGE = "igrp.departments.manage";

}
