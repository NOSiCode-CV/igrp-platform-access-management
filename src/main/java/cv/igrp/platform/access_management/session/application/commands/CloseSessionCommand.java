package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

import java.util.UUID;

/**
 * Close the caller's own session, server-side.
 *
 * <p>Distinct from the admin kill commands, which take an arbitrary session id
 * and require a permission: this one acts only on the session identified by the
 * caller's own {@code sid} claim.
 */
@Getter
public class CloseSessionCommand implements Command {

    private final String userId;
    /**
     * The caller's session, from the {@code sid} claim of their access token.
     * Null only for tokens issued without a sid, in which case the service
     * falls back to the most recently seen session and says so in the log.
     */
    private final UUID sid;
    private final String reason;

    public CloseSessionCommand(String userId, UUID sid, String reason) {
        this.userId = userId;
        this.sid = sid;
        this.reason = reason;
    }
}
