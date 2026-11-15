package cv.igrp.platform.access_management.page_access.application.queries;

import cv.igrp.framework.core.domain.Query;

/** May the caller open this page of the application? */
public record CheckPageAccessQuery(String applicationCode, String path) implements Query {
}
