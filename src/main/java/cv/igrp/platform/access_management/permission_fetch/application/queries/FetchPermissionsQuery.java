package cv.igrp.platform.access_management.permission_fetch.application.queries;

import cv.igrp.framework.core.domain.Query;

/** The caller's permission set for one application. */
public record FetchPermissionsQuery(String applicationCode) implements Query {
}
