package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.Getter;

@Getter
public class GetCurrentSessionQuery implements Query {
    private final Integer userId;

    public GetCurrentSessionQuery(Integer userId) {
        this.userId = userId;
    }
}
