package cv.igrp.platform.access_management.session.application.queries;

import cv.igrp.framework.core.domain.Query;
import lombok.Getter;

import java.util.UUID;

@Getter
public class GetCurrentSessionQuery implements Query {
    private final String userId;
    /**
     * The {@code sid} claim from the caller's JWT — the canonical id of THE
     * session this request is authenticated by. Null-tolerant for callers that
     * don't have it (falls back to "the user's most recently seen session"),
     * but should always be passed when available so callers with multiple
     * concurrent sessions get the right one.
     */
    private final UUID sid;

    public GetCurrentSessionQuery(String userId, UUID sid) {
        this.userId = userId;
        this.sid = sid;
    }
}
