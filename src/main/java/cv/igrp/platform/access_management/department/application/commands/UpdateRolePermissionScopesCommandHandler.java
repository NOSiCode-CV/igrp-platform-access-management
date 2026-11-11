package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.framework.core.domain.CommandHandler;
import cv.igrp.framework.stereotype.IgrpCommandHandler;
import cv.igrp.platform.access_management.role.domain.service.RolePermissionScopes;
import cv.igrp.platform.access_management.role.domain.service.RolePermissionsView;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionScopeChangedEvent;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.PermissionScope;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.application.dto.RolePermissionScopeDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.SUPER_ADMIN_ROLE;

/**
 * Sets the scope of some of a role's permissions (#21; spec "Data scopes").
 * The whole request is checked before anything changes: every key must be
 * held by the role, once, with a known scope. Each actual change is audited;
 * when anything changed, the role's users' permission sets are deleted after
 * commit ({@code RolePermissionChangedEvent}, #18).
 */
@Slf4j
@Component
public class UpdateRolePermissionScopesCommandHandler
        implements CommandHandler<UpdateRolePermissionScopesCommand, ResponseEntity<List<PermissionDTO>>> {

    private static final String TITLE = "Update role permission scopes";

    private final DepartmentEntityRepository departmentRepository;
    private final RoleEntityRepository roleRepository;
    private final RolePermissionScopes scopes;
    private final RolePermissionsView view;
    private final EventPublisher eventPublisher;
    private final ScopeService scopeService;

    public UpdateRolePermissionScopesCommandHandler(DepartmentEntityRepository departmentRepository,
                                                    RoleEntityRepository roleRepository,
                                                    RolePermissionScopes scopes,
                                                    RolePermissionsView view,
                                                    EventPublisher eventPublisher,
                                                    ScopeService scopeService) {
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.scopes = scopes;
        this.view = view;
        this.eventPublisher = eventPublisher;
        this.scopeService = scopeService;
    }

    @IgrpCommandHandler
    @Transactional
    public ResponseEntity<List<PermissionDTO>> handle(UpdateRolePermissionScopesCommand command) {
        DepartmentEntity department = departmentRepository.findByCodeAndStatusNotDeleted(command.getDepartmentCode());
        // Scope enforcement (R1.2): changing a role requires managing its department.
        scopeService.assertInScope(department.getId());
        RoleEntity role = roleRepository.findByDepartmentAndCodeAndStatusNot(department, command.getRoleCode(),
                        Status.DELETED)
                .orElseThrow(() -> IgrpResponseStatusException.of(HttpStatus.NOT_FOUND, TITLE,
                        "Role with code: " + command.getRoleCode() + " not found."));
        if (SUPER_ADMIN_ROLE.equals(role.getCode())) {
            // Root sees every row of every application; its scope is not set by hand.
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, TITLE, "Root always has scope all");
        }

        Map<String, PermissionScope> requested = requested(command.getScopes());
        Map<String, Held> current = currentByKey(role);
        List<String> notHeld = requested.keySet().stream().filter(key -> !current.containsKey(key)).toList();
        if (!notHeld.isEmpty()) {
            throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, TITLE,
                    "The role does not hold: " + String.join(", ", notHeld));
        }

        boolean changed = false;
        for (var entry : requested.entrySet()) {
            Held held = current.get(entry.getKey());
            PermissionScope before = held.scope();
            if (before != entry.getValue()) {
                scopes.set(role.getId(), held.permissionId(), entry.getValue());
                eventPublisher.publishSettingsAudit(new PermissionScopeChangedEvent(
                        entry.getKey(), role.getCode(), before.code(), entry.getValue().code()));
                changed = true;
            }
        }
        if (changed) {
            eventPublisher.publishRolePermissionChanged(new RolePermissionChangedEvent(
                    role.getCode(), department.getCode(), RolePermissionChangedEvent.CHANGE_SCOPES, null));
        }
        log.info("Scopes of role {} in {} updated ({} requested, changed: {})",
                role.getCode(), department.getCode(), requested.size(), changed);
        return ResponseEntity.ok(view.of(role));
    }

    /** The request by key; refuses an empty request, a duplicate key and an unknown scope. */
    private static Map<String, PermissionScope> requested(List<RolePermissionScopeDTO> entries) {
        if (entries == null || entries.isEmpty()) {
            throw IgrpResponseStatusException.badRequest("No scopes to update");
        }
        Map<String, PermissionScope> requested = new LinkedHashMap<>();
        for (RolePermissionScopeDTO entry : entries) {
            if (entry == null || entry.key() == null || entry.key().isBlank()) {
                throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, TITLE, "Every entry needs a key");
            }
            PermissionScope scope;
            try {
                scope = PermissionScope.fromCode(entry.scope());
            } catch (IllegalArgumentException e) {
                throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, TITLE, e.getMessage());
            }
            if (requested.put(entry.key(), scope) != null) {
                throw IgrpResponseStatusException.of(HttpStatus.BAD_REQUEST, TITLE,
                        "The key " + entry.key() + " appears more than once");
            }
        }
        return requested;
    }

    /** A permission the role holds: its id and current scope. */
    private record Held(int permissionId, PermissionScope scope) {
    }

    /** Each permission the role holds, by key; legacy permissions without a key are left out. */
    private Map<String, Held> currentByKey(RoleEntity role) {
        Map<Integer, PermissionScope> byId = scopes.byPermissionId(role.getId());
        Map<String, Held> byKey = new LinkedHashMap<>();
        for (PermissionEntity permission : role.getPermissions()) {
            if (permission.getPermissionKey() != null && permission.getStatus() != Status.DELETED
                    && byId.containsKey(permission.getId())) {
                byKey.put(permission.getPermissionKey(),
                        new Held(permission.getId(), byId.get(permission.getId())));
            }
        }
        return byKey;
    }
}
