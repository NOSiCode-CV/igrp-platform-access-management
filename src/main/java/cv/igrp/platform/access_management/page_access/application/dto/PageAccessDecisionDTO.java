package cv.igrp.platform.access_management.page_access.application.dto;

import java.util.List;

/**
 * The page check's answer (v3-26, #34). The frontend renders the page only when
 * {@code allowed}; otherwise it rewrites to its no-access page.
 *
 * @param page    the registered page that serves the path, when there is one
 * @param missing the page's required keys the caller lacks ({@link Reason#MISSING_PERMISSIONS})
 */
public record PageAccessDecisionDTO(boolean allowed, Reason reason, String page, List<String> missing) {

    /** Why, in the order the checks run: the first that decides is the reason. */
    public enum Reason {
        /** Pages are for users; a service account token gets no page. */
        NOT_A_USER,
        /** No role valid for the application (or no such active application: the same answer, so codes cannot be probed). */
        NO_APPLICATION_ACCESS,
        /** No registered page of the application serves the path (default deny, for root too). */
        PAGE_NOT_REGISTERED,
        /** Root: every registered page of every application. */
        ROOT,
        /** Several roles are valid and none is chosen: the frontend asks the user to pick one. */
        NO_ACTIVE_ROLE,
        /** The active role lacks some of the page's required keys. */
        MISSING_PERMISSIONS,
        /** Application access and every required key. */
        ALLOWED
    }

    public static PageAccessDecisionDTO denied(Reason reason) {
        return new PageAccessDecisionDTO(false, reason, null, List.of());
    }
}
