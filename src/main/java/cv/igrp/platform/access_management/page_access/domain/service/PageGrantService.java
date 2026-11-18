package cv.igrp.platform.access_management.page_access.domain.service;

import cv.igrp.platform.access_management.department.application.commands.AddPermissionsCommand;
import cv.igrp.platform.access_management.department.application.commands.AddPermissionsCommandHandler;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantRequestDTO;
import cv.igrp.platform.access_management.page_access.application.dto.PageGrantResultDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import jakarta.persistence.EntityManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Grants a page to a role (v3-27, #35; spec "Frontend"): the role gets the
 * page's required keys and the optional ones the administrator chose. Nothing
 * links the role to the page; only the permissions are assigned.
 *
 * <p>The assignment goes through the role's own permission grant
 * ({@link AddPermissionsCommandHandler}, called directly: the command bus
 * depends on every handler, so a handler cannot depend on it), so its rules
 * hold: the caller's department scope, the parent role's permissions, and the
 * keys available to the role's department. All or nothing: when the role
 * cannot hold every key, nothing is granted and the answer names the keys.
 */
@Service
public class PageGrantService {

    private static final String PAGE_KEYS = """
            SELECT pp.permission_key, pp.required FROM t_application_page_permission pp
              JOIN t_application_page p ON p.id = pp.page_id
             WHERE p.page_key = ? AND p.status = 'ACTIVE'""";

    private static final String NAMES = """
            SELECT permission_key, name FROM t_permission
             WHERE permission_key IN (:keys) AND status <> 'DELETED'""";

    private static final String HELD = """
            SELECT p.permission_key FROM t_role_permission rp
              JOIN t_role r ON r.id = rp.role_id
              JOIN t_department d ON d.id = r.department
              JOIN t_permission p ON p.id = rp.permission
             WHERE d.code = :department AND r.code = :role AND r.status <> 'DELETED'
               AND p.permission_key IN (:keys)""";

    private static final String PERMISSION_KEY = "permission_key";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AddPermissionsCommandHandler addPermissions;
    private final EntityManager entityManager;

    public PageGrantService(JdbcTemplate jdbc, AddPermissionsCommandHandler addPermissions,
                            EntityManager entityManager) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.addPermissions = addPermissions;
        this.entityManager = entityManager;
    }

    @Transactional
    public PageGrantResultDTO grant(String departmentCode, String roleCode, PageGrantRequestDTO request) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM t_application_page WHERE page_key = ? AND status = 'ACTIVE')",
                Boolean.class, request.page()))) {
            throw IgrpResponseStatusException.notFound("Page " + request.page() + " is not registered");
        }
        Set<String> keys = new TreeSet<>();
        Set<String> optional = new HashSet<>();
        jdbc.query(PAGE_KEYS, rs -> {
            if (rs.getBoolean("required")) {
                keys.add(rs.getString(PERMISSION_KEY));
            } else {
                optional.add(rs.getString(PERMISSION_KEY));
            }
        }, request.page());
        for (String chosen : request.optionalKeys()) {
            if (!optional.contains(chosen)) {
                throw IgrpResponseStatusException.badRequest(
                        "Key " + chosen + " is not an optional key of page " + request.page());
            }
            keys.add(chosen);
        }
        if (keys.isEmpty()) {
            return new PageGrantResultDTO(request.page(), List.of());
        }

        Map<String, String> names = new HashMap<>();
        named.query(NAMES, Map.of("keys", keys),
                (RowCallbackHandler) rs -> names.put(rs.getString(PERMISSION_KEY), rs.getString("name")));
        Set<String> unregistered = new TreeSet<>(keys);
        unregistered.removeAll(names.keySet());
        if (!unregistered.isEmpty()) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Page " + request.page()
                    + " needs keys no service has registered yet: " + String.join(", ", unregistered));
        }

        // The grant endpoints look permissions up by name (#12).
        addPermissions.handle(new AddPermissionsCommand(new ArrayList<>(names.values()), departmentCode, roleCode));

        // The grant saved through JPA; the check below reads with JDBC.
        entityManager.flush();
        Set<String> missing = new TreeSet<>(keys);
        missing.removeAll(named.queryForList(HELD,
                Map.of("department", departmentCode, "role", roleCode, "keys", keys), String.class));
        if (!missing.isEmpty()) {
            throw IgrpResponseStatusException.of(HttpStatus.CONFLICT, "Role " + roleCode + " cannot hold page "
                    + request.page() + ": its department, or its parent role, lacks " + String.join(", ", missing));
        }
        return new PageGrantResultDTO(request.page(), List.copyOf(keys));
    }
}
