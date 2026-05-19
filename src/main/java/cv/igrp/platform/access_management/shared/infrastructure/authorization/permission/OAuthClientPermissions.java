package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

public final class OAuthClientPermissions {

    private OAuthClientPermissions() {
    }

    @IgrpPermission(name = "igrp.client.list", description = "Permission to list OAuth clients")
    public static final String IGRP_CLIENT_LIST = "igrp.client.list";

    @IgrpPermission(name = "igrp.client.view", description = "Permission to view OAuth client")
    public static final String IGRP_CLIENT_VIEW = "igrp.client.view";

    @IgrpPermission(name = "igrp.client.create", description = "Permission to create OAuth client")
    public static final String IGRP_CLIENT_CREATE = "igrp.client.create";

    @IgrpPermission(name = "igrp.client.update", description = "Permission to update OAuth client")
    public static final String IGRP_CLIENT_UPDATE = "igrp.client.update";

    @IgrpPermission(name = "igrp.client.delete", description = "Permission to delete OAuth client")
    public static final String IGRP_CLIENT_DELETE = "igrp.client.delete";

    @IgrpPermission(name = "igrp.client.manage", description = "Permission to manage OAuth client")
    public static final String IGRP_CLIENT_MANAGE = "igrp.client.manage";

}
