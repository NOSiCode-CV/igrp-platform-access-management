package cv.igrp.platform.access_management.department.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer counters for the department-management scope enforcement.
 *
 * <p>Bumped by {@link cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService}
 * on every denial — a scoped-manager trying to act outside their subtree, or a
 * non-superadmin trying to create a root department. Ops can alert on spikes
 * as a signal of misconfiguration or privilege-escalation probing.
 *
 * <p>Names follow the {@code igrp_department_*_total} convention so they
 * survive the dot-to-underscore mapping in Prometheus exposition unchanged.
 */
@Component
public class DepartmentScopeMetrics {

    public static final String SCOPE_CHECK_DENIED = "igrp.department.scope.check.denied";

    private final MeterRegistry registry;

    public DepartmentScopeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Increment the denial counter. {@code reason} is either
     * {@code "OUT_OF_SCOPE"} (scoped manager acting outside their subtree) or
     * {@code "ROOT_DEPARTMENT_FORBIDDEN"} (non-superadmin trying to create a
     * root department).
     */
    public void recordScopeDenied(String reason) {
        Counter.builder(SCOPE_CHECK_DENIED)
                .description("Department-management operations denied by the scope check")
                .tag("reason", reason == null ? "unknown" : reason)
                .register(registry)
                .increment();
    }
}
