package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

/**
 * Phase E5 — terminate every active session belonging to a user identified by
 * {@code userExternalId}. Used by {@code POST /api/admin/users/{u}/logout-all}.
 */
@Getter
public class KillAllUserSessionsCommand implements Command {

    private final String userExternalId;
    private final String reason;
    private final String killedBy;

    public KillAllUserSessionsCommand(String userExternalId, String reason, String killedBy) {
        this.userExternalId = userExternalId;
        this.reason = reason;
        this.killedBy = killedBy;
    }
}
