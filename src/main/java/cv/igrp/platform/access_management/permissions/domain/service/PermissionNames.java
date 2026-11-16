package cv.igrp.platform.access_management.permissions.domain.service;

import cv.igrp.platform.access_management.permissions.application.dto.PermissionNameUpdateDTO;
import cv.igrp.platform.access_management.permissions.application.dto.PermissionSummaryDTO;
import cv.igrp.platform.access_management.routes.domain.service.RouteKeys;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionNameEditedEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Display names of permissions (v3-04, #12; spec "Permission entity"). Code
 * sets a key's name and description only when the key is first registered;
 * administrators edit them here, by the immutable key, and registration never
 * overwrites them.
 *
 * <p>Names identify permissions in the grant endpoints (by department and
 * role), so a name another live permission has is refused.
 */
@Service
public class PermissionNames {

    private static final String LIST = """
            SELECT p.permission_key, p.name, p.description, p.service, a.code AS application_code
              FROM t_permission p
              LEFT JOIN t_application a ON a.id = p.application_id
             WHERE p.permission_key IS NOT NULL AND p.status <> 'DELETED'
               AND (CAST(? AS varchar) IS NULL OR a.code = ?)
               AND (CAST(? AS varchar) IS NULL OR p.service = ?)
             ORDER BY lower(coalesce(p.name, p.permission_key)), p.permission_key""";

    /** First half of the advisory lock key: one lock per display name, so two edits never both take a name. */
    private static final int NAME_LOCK_CLASS = 0x504E414D; // "PNAM"

    private final PermissionEntityRepository permissions;
    private final JdbcTemplate jdbc;
    private final EventPublisher events;

    public PermissionNames(PermissionEntityRepository permissions, JdbcTemplate jdbc, EventPublisher events) {
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.events = events;
    }

    /** The live keyed permissions, by display name; the key stands in for a missing name. */
    public List<PermissionSummaryDTO> list(String applicationCode, String service) {
        return jdbc.query(LIST, (rs, i) -> new PermissionSummaryDTO(
                        rs.getString("permission_key"),
                        Objects.requireNonNullElse(rs.getString("name"), rs.getString("permission_key")),
                        rs.getString("description"), rs.getString("service"), rs.getString("application_code")),
                applicationCode, applicationCode, service, service);
    }

    /**
     * @throws IgrpResponseStatusException 404 for an unknown or deleted key; 400 for a name in the
     *                                     reserved {@code igrp.} namespace; 409 for a name another
     *                                     live permission has
     */
    @Transactional
    public PermissionSummaryDTO update(String key, PermissionNameUpdateDTO update) {
        PermissionEntity permission = permissions.findByPermissionKeyIn(List.of(key)).stream()
                .filter(p -> p.getStatus() != Status.DELETED)
                .findFirst()
                .orElseThrow(() -> IgrpResponseStatusException.notFound("Permission " + key + " not found"));
        String name = update.name().trim();
        String problem = RouteKeys.nameProblem(name);
        if (problem != null) {
            throw IgrpResponseStatusException.badRequest("Invalid permission name: " + problem);
        }
        String description = update.description() == null || update.description().isBlank()
                ? null : update.description().trim();
        if (name.equals(permission.getName()) && Objects.equals(description, permission.getDescription())) {
            return summary(permission);
        }
        // Serialises edits to the same name: the check below then sees a committed rival.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?, hashtext(?))::text", String.class, NAME_LOCK_CLASS, name);
        permissions.findByNameAndStatusNot(name, Status.DELETED)
                .filter(other -> !Objects.equals(other.getId(), permission.getId()))
                .ifPresent(other -> {
                    throw IgrpResponseStatusException.of(HttpStatus.CONFLICT,
                            "Name " + name + " is already the name of another permission");
                });
        PermissionNameEditedEvent event = new PermissionNameEditedEvent(key, permission.getService(),
                PermissionNameEditedEvent.label(permission.getName(), permission.getDescription()),
                PermissionNameEditedEvent.label(name, description));
        permission.setName(name);
        permission.setDescription(description);
        permissions.save(permission);
        events.publishSettingsAudit(event);
        return summary(permission);
    }

    private static PermissionSummaryDTO summary(PermissionEntity permission) {
        String applicationCode = permission.getApplication() == null ? null : permission.getApplication().getCode();
        return new PermissionSummaryDTO(permission.getPermissionKey(), permission.getName(),
                permission.getDescription(), permission.getService(), applicationCode);
    }
}
