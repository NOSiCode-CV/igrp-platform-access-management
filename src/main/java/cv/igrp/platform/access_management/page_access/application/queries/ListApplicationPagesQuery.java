package cv.igrp.platform.access_management.page_access.application.queries;

import cv.igrp.framework.core.domain.Query;

/** The registered pages of an application. */
public record ListApplicationPagesQuery(String applicationCode) implements Query {
}
