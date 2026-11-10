package cv.igrp.platform.access_management.shared.infrastructure.persistence.revision;

import cv.igrp.platform.access_management.shared.config.ApplicationAuditorAware;
import cv.igrp.platform.access_management.shared.infrastructure.correlation.CorrelationIds;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RevisionInfoEntity;
import org.hibernate.envers.RevisionListener;

/**
 * Fills {@link RevisionInfoEntity}'s username and trace id from the thread that
 * makes the change. The username is resolved exactly like {@code created_by}
 * ({@link ApplicationAuditorAware}), so a revision and the row it audits agree.
 * Envers creates the listener itself, so it holds no Spring beans.
 */
public class RevisionInfoListener implements RevisionListener {

    // A plain instance, not the Spring bean: Envers creates this listener itself.
    // ApplicationAuditorAware is stateless, so both resolve the user identically.
    private static final ApplicationAuditorAware AUDITOR = new ApplicationAuditorAware();

    @Override
    public void newRevision(Object revisionEntity) {
        RevisionInfoEntity revision = (RevisionInfoEntity) revisionEntity;
        revision.setUsername(AUDITOR.getCurrentAuditor().orElse("system"));
        // Outside a request (schedulers) the change still gets an id of its own.
        revision.setTraceId(CorrelationIds.current().orElseGet(() -> CorrelationIds.resolve(null)));
    }
}
