package cv.igrp.platform.access_management.shared.infrastructure.authorization.permission;

import cv.igrp.framework.stereotype.IgrpPermission;

/**
 * Business permissions for the {@code /api/service-accounts} admin surface.
 * Mirrors the shape of {@link OAuthClientPermissions} so the two admin
 * pages in Application Center gate consistently.
 *
 * <p>Historically the ServiceAccountController shipped without
 * {@code @PreAuthorize} annotations — every authenticated caller could
 * list, create, edit, or delete service accounts. This class closes that
 * gap and is referenced from the controller via {@code T(Permission)} SpEL.
 * See the "Client &amp; Service Account Management Spec" §7 backend
 * follow-up #1.
 */
public final class ServiceAccountPermissions {

    private ServiceAccountPermissions() {
    }

    @IgrpPermission(name = "igrp.service_account.list", description = "Permission to list service accounts")
    public static final String IGRP_SERVICE_ACCOUNT_LIST = "igrp.service_account.list";

    @IgrpPermission(name = "igrp.service_account.view", description = "Permission to view a service account")
    public static final String IGRP_SERVICE_ACCOUNT_VIEW = "igrp.service_account.view";

    @IgrpPermission(name = "igrp.service_account.create", description = "Permission to create a service account")
    public static final String IGRP_SERVICE_ACCOUNT_CREATE = "igrp.service_account.create";

    @IgrpPermission(name = "igrp.service_account.update", description = "Permission to update a service account")
    public static final String IGRP_SERVICE_ACCOUNT_UPDATE = "igrp.service_account.update";

    @IgrpPermission(name = "igrp.service_account.delete", description = "Permission to delete a service account")
    public static final String IGRP_SERVICE_ACCOUNT_DELETE = "igrp.service_account.delete";

    @IgrpPermission(name = "igrp.service_account.manage", description = "Permission to manage service accounts (superset)")
    public static final String IGRP_SERVICE_ACCOUNT_MANAGE = "igrp.service_account.manage";
}
