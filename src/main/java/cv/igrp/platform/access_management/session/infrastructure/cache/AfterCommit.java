package cv.igrp.platform.access_management.session.infrastructure.cache;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a cache eviction once the surrounding transaction has committed.
 *
 * <p>Evicting earlier leaves a window in which a concurrent request misses the
 * cache, reads the row still ACTIVE, and caches it again, so a revoked session
 * would keep working until the entry expires.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
