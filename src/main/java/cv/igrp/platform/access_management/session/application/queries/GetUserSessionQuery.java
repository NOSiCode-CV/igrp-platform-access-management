package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import cv.igrp.framework.core.domain.IgrpEnum;
import lombok.Getter;

@Getter
public class GetUserSessionQuery implements Query {
    private final String userExternalId;
    
    public GetUserSessionQuery(String userExternalId) {
        this.userExternalId = userExternalId;
    }
}
