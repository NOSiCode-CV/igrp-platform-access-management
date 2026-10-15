package cv.igrp.platform.access_management.session.application.commands;

import cv.igrp.framework.core.domain.Command;
import lombok.Getter;

import java.util.UUID;

@Getter
public class RefreshSessionCommand implements Command {
    private final String userId;
    /**
     * The caller's own session, from the {@code sid} claim of their access
     * token. Required to tell WHICH session to extend: several applications in
     * one browser each hold their own, and without this the service fell back to
     * the user's most recently seen session — frequently a sibling's.
     * Null only for tokens issued without a sid.
     */
    private final UUID sid;
    private final Integer extensionSeconds;

    public RefreshSessionCommand(String userId, UUID sid, Integer extensionSeconds) {
        this.userId = userId;
        this.sid = sid;
        this.extensionSeconds = extensionSeconds;
    }
}
