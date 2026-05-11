package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.Getter;

import java.util.UUID;

/**
 * Query for {@code GET /api/session/check}. Identifies the bound session via
 * the JWT {@code sid} claim; the {@code sub} is included for diagnostic
 * purposes and audit context only — server-side resolution does not depend
 * on it.
 */
@Getter
public class GetSessionCheckQuery implements Query {

    private final UUID sid;
    private final String sub;

    public GetSessionCheckQuery(UUID sid, String sub) {
        this.sid = sid;
        this.sub = sub;
    }
}
