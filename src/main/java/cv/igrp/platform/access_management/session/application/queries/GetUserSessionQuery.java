package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.Getter;

@Getter
public class GetUserSessionQuery implements Query {
    private final Integer userId;

    public GetUserSessionQuery(Integer userId) {
        this.userId = userId;
    }
}
