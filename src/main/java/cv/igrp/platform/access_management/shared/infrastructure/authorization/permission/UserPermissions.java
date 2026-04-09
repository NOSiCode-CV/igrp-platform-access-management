package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class UserPermissions {

    private UserPermissions() {
    }

    @IgrpPermission(name = "igrp.users.create", description = "Permission to create user")
    public static final String IGRP_USERS_CREATE = "igrp.users.create";

    @IgrpPermission(name = "igrp.users.update", description = "Permission to update user")
    public static final String IGRP_USERS_UPDATE = "igrp.users.update";

    @IgrpPermission(name = "igrp.users.delete", description = "Permission to delete user")
    public static final String IGRP_USERS_DELETE = "igrp.users.delete";

    @IgrpPermission(name = "igrp.users.view", description = "Permission to view user")
    public static final String IGRP_USERS_VIEW = "igrp.users.view";

    @IgrpPermission(name = "igrp.users.list", description = "Permission to list users")
    public static final String IGRP_USERS_LIST = "igrp.users.list";

    @IgrpPermission(name = "igrp.users.manage", description = "Permission to manage user")
    public static final String IGRP_USERS_MANAGE = "igrp.users.manage";

    @IgrpPermission(name = "igrp.users.pii.view", description = "Permission to view user PII")
    public static final String IGRP_USERS_PII_VIEW = "igrp.users.pii.view";

    @IgrpPermission(name = "igrp.users.pii.update", description = "Permission to update user PII")
    public static final String IGRP_USERS_PII_UPDATE = "igrp.users.pii.update";

}
