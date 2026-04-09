package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class SessionPermissions {

    private SessionPermissions() {
    }

    @IgrpPermission(name = "igrp.session.admin", description = "Permission to administer sessions (view, kill, manage all sessions)")
    public static final String IGRP_SESSION_ADMIN = "igrp.session.admin";

    @IgrpPermission(name = "igrp.session.view", description = "Permission to view sessions")
    public static final String IGRP_SESSION_VIEW = "igrp.session.view";

    @IgrpPermission(name = "igrp.session.kill", description = "Permission to kill/terminate sessions")
    public static final String IGRP_SESSION_KILL = "igrp.session.kill";

    @IgrpPermission(name = "igrp.session.create", description = "Permission to create sessions")
    public static final String IGRP_SESSION_CREATE = "igrp.session.create";

    @IgrpPermission(name = "igrp.session.manage", description = "Permission to manage own sessions")
    public static final String IGRP_SESSION_MANAGE = "igrp.session.manage";

}
