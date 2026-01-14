package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class UserPermissions {

    private UserPermissions() {
    }

    @IgrpPermission(name = "igrp.user.create", description = "Permission to create user")
    public static final String IGRP_USER_CREATE = "igrp.user.create";

    @IgrpPermission(name = "igrp.user.update", description = "Permission to update user")
    public static final String IGRP_USER_UPDATE = "igrp.user.update";

    @IgrpPermission(name = "igrp.user.delete", description = "Permission to delete user")
    public static final String IGRP_USER_DELETE = "igrp.user.delete";

    @IgrpPermission(name = "igrp.user.view", description = "Permission to view user")
    public static final String IGRP_USER_VIEW = "igrp.user.view";

    @IgrpPermission(name = "igrp.user.list", description = "Permission to list users")
    public static final String IGRP_USER_LIST = "igrp.user.list";

    @IgrpPermission(name = "igrp.user.manage", description = "Permission to manage user")
    public static final String IGRP_USER_MANAGE = "igrp.user.manage";

}
