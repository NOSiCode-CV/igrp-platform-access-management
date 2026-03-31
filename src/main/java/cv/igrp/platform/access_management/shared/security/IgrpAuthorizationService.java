package cv.igrp.platform.access_management.shared.security;

import cv.igrp.framework.auth.generated.PermissionsRegistry;
import cv.igrp.platform.access_management.authorization.application.commands.handler.SingleCheckAuthorizationHandler;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckRequestDTO;
import cv.igrp.platform.access_management.authorization.application.dto.PermissionCheckResponseDTO;
import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditCategory;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditEventType;
import cv.igrp.platform.access_management.shared.security.policy.Policy;
import cv.igrp.platform.access_management.shared.security.policy.PolicyDecision;
import cv.igrp.platform.access_management.shared.security.policy.ResourceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service("igrpAuthorization")
@SuppressWarnings("unused")
public class IgrpAuthorizationService {

    private final SingleCheckAuthorizationHandler authorizationHandler;
    private final AuthenticationHelper authHelper;
    private final List<Policy> policies;
    private final SecurityAuditService auditService;

    public IgrpAuthorizationService(
            SingleCheckAuthorizationHandler authorizationHandler,
            AuthenticationHelper authHelper,
            List<Policy> policies,
            SecurityAuditService auditService) {
        this.authorizationHandler = authorizationHandler;
        this.authHelper = authHelper;
        this.policies = policies;
        this.auditService = auditService;
    }

    /**
     * Enterprise decision pipeline (6 steps).
     *
     * @param action            the permission code (e.g. "users.create")
     * @param resourceRef       optional resource identifier
     * @param contextAttributes optional attributes for ABAC policies
     * @return true if allowed, false otherwise
     */
    public boolean allow(String action, String resourceRef, Map<String, Object> contextAttributes) {
        // 1. Authenticate
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        String subject = authHelper.getSub();

        // 2 & 3. Load roles and permissions (Permission Gate - Fast path)
        var permissionResponse = authorizationHandler.checkAuthorization(subject, action, resourceRef);
        if (!permissionResponse.isAllowed()) {
            auditService.logAccessDenied(action, "Permission gate denied by role/permission evaluation");
            return false;
        }

        // 4 & 5. Resolve context and validate policy rules (ABAC - Context path)
        ResourceContext context = contextAttributes != null ? ResourceContext.of(contextAttributes) : ResourceContext.empty();

        for (Policy policy : policies) {
            PolicyDecision decision = policy.evaluate(authentication, action, context);
            if (!decision.allowed()) {
                // 6. Audit the decision (Denied)
                auditService.logEvent(AuditEventType.ACCESS_DENIED, AuditCategory.AUTHORIZATION, Map.of(
                        "subject", subject,
                        "action", action,
                        "resource", resourceRef != null ? resourceRef : "N/A",
                        "decisionReason", decision.reason(),
                        "policy", policy.getClass().getSimpleName()
                ));
                return false;
            }
        }

        // 6. Audit the decision (Optional: Allowed)
        // For highly sensitive actions, we might want to log access granted too.

        return true;
    }

    /**
     * Checks if the current user has a specific permission.
     *
     * @param action the permission enum (e.g. "Permission.FINANCE_SALARY_VIEW")
     * @return true if allowed, false otherwise
     */
    public boolean checkPermission(PermissionsRegistry.Permission action) {
        return allow(action.getCode(), null, null);
    }

    /**
     * Checks if the user has ALL the given permissions.
     *
     * @param actions list or varargs of permission enums
     * @return true only if ALL are allowed
     */
    public boolean checkAllPermissions(PermissionsRegistry.Permission... actions) {
        if (actions == null || actions.length == 0) {
            return false;
        }
        return Arrays.stream(actions).allMatch(this::checkPermission);
    }

    /**
     * Checks if the user has ANY of the given permissions.
     *
     * @param actions list or varargs of permission enums
     * @return true if at least one is allowed
     */
    public boolean checkAnyPermission(PermissionsRegistry.Permission... actions) {
        if (actions == null || actions.length == 0) {
            return false;
        }
        return Arrays.stream(actions).anyMatch(this::checkPermission);
    }
}