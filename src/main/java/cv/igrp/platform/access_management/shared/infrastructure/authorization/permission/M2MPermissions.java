package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class M2MPermissions {

    private M2MPermissions() {
    }

    @IgrpPermission(name = "igrp.m2m.sync", description = "Permission to sync machine-to-machine")
    public static final String IGRP_M2M_SYNC = "igrp.m2m.sync";

}
