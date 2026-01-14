package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class DepartmentPermissions {

    private DepartmentPermissions() {
    }

    @IgrpPermission(name = "igrp.department.create", description = "Permission to create department")
    public static final String IGRP_DEPARTMENT_CREATE = "igrp.department.create";

    @IgrpPermission(name = "igrp.department.update", description = "Permission to update department")
    public static final String IGRP_DEPARTMENT_UPDATE = "igrp.department.update";

    @IgrpPermission(name = "igrp.department.delete", description = "Permission to delete department")
    public static final String IGRP_DEPARTMENT_DELETE = "igrp.department.delete";

    @IgrpPermission(name = "igrp.department.view", description = "Permission to view department")
    public static final String IGRP_DEPARTMENT_VIEW = "igrp.department.view";

    @IgrpPermission(name = "igrp.department.list", description = "Permission to list departments")
    public static final String IGRP_DEPARTMENT_LIST = "igrp.department.list";

    @IgrpPermission(name = "igrp.department.manage", description = "Permission to manage department")
    public static final String IGRP_DEPARTMENT_MANAGE = "igrp.department.manage";

}
