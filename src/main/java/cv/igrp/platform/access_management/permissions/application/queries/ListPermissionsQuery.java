package cv.igrp.platform.access_management.permissions.application.queries;

import cv.igrp.framework.core.domain.Query;

/** The keyed permissions, optionally of one application or one service. */
public record ListPermissionsQuery(String applicationCode, String service) implements Query {
}
