package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import cv.igrp.framework.core.domain.IgrpEnum;
import lombok.Getter;

@Getter
public class GetCurrentSessionQuery implements Query {
    private final String userExternalId;
    
    public GetCurrentSessionQuery(String userExternalId) {
        this.userExternalId = userExternalId;
    }
}
