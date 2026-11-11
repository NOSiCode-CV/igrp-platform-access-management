package cv.igrp.platform.access_management.active_role.application.queries;

import cv.igrp.framework.core.domain.Query;

/** The caller's active role in one application. */
public record GetActiveRoleQuery(String applicationCode) implements Query {
}
