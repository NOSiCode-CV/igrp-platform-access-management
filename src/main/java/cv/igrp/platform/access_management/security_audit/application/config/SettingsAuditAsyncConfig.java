package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditContextProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;

/**
 * Dedicated executor for the {@link SettingsAuditEventListener}.
 *
 * <p>Settings audit writes run asynchronously so a slow or failing audit never
 * blocks (nor breaks — see {@code requirements.md} N4) the admin action that
 * triggered them. But the audit row must still carry the request's IP address,
 * user agent and the acting user (validation §"Behaviour — happy paths"), and
 * those live in thread-bound holders ({@code RequestContextHolder} /
 * {@code SecurityContextHolder}) that are empty on a worker thread — and the
 * servlet request may even be recycled by the time the worker runs.
 *
 * <p>The {@link TaskDecorator} below resolves the audit context <em>eagerly on the
 * request thread</em> (via {@link SecurityAuditContextProvider#withCapturedContext})
 * and re-binds it — together with the {@link SecurityContext} for JPA auditing —
 * around the worker execution. This keeps the async decoupling while preserving
 * accurate request attribution.
 */
@Configuration
public class SettingsAuditAsyncConfig {

    public static final String EXECUTOR_BEAN = "settingsAuditExecutor";

    @Bean(name = EXECUTOR_BEAN)
    public Executor settingsAuditExecutor(SecurityAuditContextProvider contextProvider) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("settings-audit-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setTaskDecorator(auditContextDecorator(contextProvider));
        executor.initialize();
        return executor;
    }

    private TaskDecorator auditContextDecorator(SecurityAuditContextProvider contextProvider) {
        return runnable -> {
            // Captured on the submitting (request) thread.
            SecurityContext securityContext = SecurityContextHolder.getContext();
            Runnable withAuditContext = contextProvider.withCapturedContext(runnable);
            return () -> {
                SecurityContext previous = SecurityContextHolder.getContext();
                SecurityContextHolder.setContext(securityContext);
                try {
                    withAuditContext.run();
                } finally {
                    SecurityContextHolder.setContext(previous);
                }
            };
        };
    }
}
