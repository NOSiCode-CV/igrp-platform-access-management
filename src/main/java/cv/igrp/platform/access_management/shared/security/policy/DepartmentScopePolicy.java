package cv.igrp.platform.access_management.shared.security.policy;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Policy that checks if the subject has a role in the department associated with the resource.
 */
@Component
public class DepartmentScopePolicy implements Policy {

    private final AuthenticationHelper authHelper;
    private final IGRPUserEntityRepository userRepository;

    public DepartmentScopePolicy(AuthenticationHelper authHelper, IGRPUserEntityRepository userRepository) {
        this.authHelper = authHelper;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyDecision evaluate(Authentication authentication, String action, ResourceContext context) {
        String departmentCode = context.getStringAttribute("departmentCode");
        if (departmentCode == null) {
            return PolicyDecision.allow(); // No department constraint
        }

        // authHelper.getSub() returns this AS's `sub` claim, which is the user's
        // UUID (the `id` field) — populated either from `IgrpOidcUser.getUserProfile().id()`
        // for the native OIDC flow or from `jwt.getClaimAsString("sub")` for the
        // Bearer flow, both of which JwtTokenConfig populates with the user's id
        // via `context.getClaims().subject(internalSub)`. The `username` column
        // stores the UPSTREAM IdP's sub (Autentika / Keycloak / etc.), not our
        // own id, so `findByUsername(subject)` 404'd every authorization check
        // for OIDC-provisioned users. Switch to id-based lookup.
        String subject = authHelper.getSub();
        IGRPUserEntity user = userRepository.findById(subject)
                .orElseThrow(() -> new RuntimeException("User not found: " + subject));

        boolean hasRoleInDepartment = user.getRoles().stream()
                .anyMatch(role -> role.getDepartment() != null && role.getDepartment().getCode().equals(departmentCode));

        if (hasRoleInDepartment) {
            return PolicyDecision.allow();
        }

        return PolicyDecision.deny("Subject " + subject + " does not have a role in department: " + departmentCode);
    }
}
