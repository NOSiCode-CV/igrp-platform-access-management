package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class AuditPermissions {

    private AuditPermissions() {
    }

    @IgrpPermission(name = "igrp.audit.view", description = "Permission to view audit logs")
    public static final String IGRP_AUDIT_VIEW = "igrp.audit.view";

    @IgrpPermission(name = "igrp.audit.view_by_user", description = "Permission to view audit logs by user")
    public static final String IGRP_AUDIT_VIEW_BY_USER = "igrp.audit.view_by_user";

    @IgrpPermission(name = "igrp.audit.list", description = "Permission to list audit logs")
    public static final String IGRP_AUDIT_LIST = "igrp.audit.list";

}
