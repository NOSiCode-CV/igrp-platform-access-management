package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);
    private static final String SYSTEM_USER = "system";
    private static final String SUPER_ADMIN_USERNAME = "superadmin";
    private static final String IGRP_DEPARTMENT = "DEPT_IGRP";
    private static final String IGRP_APP = "APP_IGRP";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ConfigurationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Async
    public void initializeSystemConfiguration() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[Startup Config] Starting system initialization...");

        try {
            // 1. Bulk existence checks
            boolean superAdminExists = exists("SELECT 1 FROM t_user WHERE username='%s' LIMIT 1".formatted(SUPER_ADMIN_USERNAME));
            boolean departmentExists = exists("SELECT 1 FROM t_department WHERE code='%s' LIMIT 1".formatted(IGRP_DEPARTMENT));
            boolean appExists = exists("SELECT 1 FROM t_application WHERE type='SYSTEM' LIMIT 1");
            boolean permissionExists = exists("SELECT 1 FROM t_permission WHERE name='manage_access' LIMIT 1");
            boolean roleExists = exists("SELECT 1 FROM t_role WHERE name='%s' LIMIT 1".formatted(SUPER_ADMIN_USERNAME));

            // 2. Create missing entities in optimized order
            Long departmentId = departmentExists ? getId("SELECT id FROM t_department WHERE code='%s'".formatted(IGRP_DEPARTMENT)) :
                    createDefaultDepartment();

            Long appId = appExists ? getId("SELECT id FROM t_application WHERE type='SYSTEM' LIMIT 1") :
                    createDefaultApp(departmentId);

            Long permissionId = permissionExists ? getId("SELECT id FROM t_permission WHERE name='manage_access'") :
                    createDefaultPermission(appId);

            Long roleId = roleExists ? getId("SELECT id FROM t_role WHERE name='%s'".formatted(SUPER_ADMIN_USERNAME)) :
                    createDefaultRole(departmentId, permissionId);

            Long userId = superAdminExists ? getId("SELECT id FROM t_user WHERE username='%s'".formatted(SUPER_ADMIN_USERNAME)) :
                    createSuperAdminUser();

            // Assign role to superadmin
            assignRoleToSuperAdminUser(roleId, userId);

            // Create default menus
            createDefaultMenus(appId);

            LOGGER.info("[Startup Config] System initialization completed in {} ms",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            LOGGER.error("[Startup Config] Error initializing system: {}", e.getMessage(), e);
        }
    }

    // =====================================================
    // Default entity creation with audit columns
    // =====================================================

    private Long createDefaultDepartment() {
        String sql = """
            INSERT INTO t_department
            (name, code, description, status,
             created_by, created_date, last_modified_by, last_modified_date)
            VALUES (?, ?, ?, 'ACTIVE', ?, now(), ?, now())
            RETURNING id
        """;
        LOGGER.info("[Startup Config] Default Department created");
        return jdbcTemplate.queryForObject(sql,
                Long.class,
                "iGRP", IGRP_DEPARTMENT, "iGRP Department", SYSTEM_USER, SYSTEM_USER);
    }

    private Long createDefaultApp(Long deptId) {
        String sql = """
            INSERT INTO t_application
            (name, code, description, owner, department_id, status, type,
             created_by, created_date, last_modified_by, last_modified_date)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'SYSTEM', ?, now(), ?, now())
            RETURNING id
        """;
        LOGGER.info("[Startup Config] Default App created");
        return jdbcTemplate.queryForObject(sql,
                Long.class,
                "iGRP", IGRP_APP, "iGRP Application", SUPER_ADMIN_USERNAME, deptId,
                SYSTEM_USER, SYSTEM_USER);
    }

    private Long createDefaultPermission(Long appId) {
        String sql = """
            INSERT INTO t_permission
            (name, description, status, application,
             created_by, created_date, last_modified_by, last_modified_date)
            VALUES (?, ?, 'ACTIVE', ?, ?, now(), ?, now())
            RETURNING id
        """;
        LOGGER.info("[Startup Config] Default Permission created");
        return jdbcTemplate.queryForObject(sql,
                Long.class,
                "manage_access", "iGRP Manage Access Permission", appId,
                SYSTEM_USER, SYSTEM_USER);
    }

    private Long createDefaultRole(Long deptId, Long permId) {
        // Insert role
        String sqlRole = """
            INSERT INTO t_role
            (name, description, status, department,
             created_by, created_date, last_modified_by, last_modified_date)
            VALUES (?, ?, 'ACTIVE', ?, ?, now(), ?, now())
            RETURNING id
        """;
        Long roleId = jdbcTemplate.queryForObject(sqlRole,
                Long.class,
                SUPER_ADMIN_USERNAME, "iGRP Superadmin", deptId,
                SYSTEM_USER, SYSTEM_USER);

        // Insert role-permission relation
        String sqlRolePerm = """
            INSERT INTO t_role_permission
            (role_id, permission)
            SELECT ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM t_role_permission WHERE role_id=? AND permission=?
            )
        """;
        jdbcTemplate.update(sqlRolePerm, roleId, permId, roleId, permId);

        LOGGER.info("[Startup Config] Default Role created");
        return roleId;
    }

    private Long createSuperAdminUser() {
        String sql = """
            INSERT INTO t_user
            (name, username, email,
             created_by, created_date, last_modified_by, last_modified_date)
            VALUES (?, ?, ?, ?, now(), ?, now())
            RETURNING id
        """;
        LOGGER.info("[Startup Config] Super admin user created");
        return jdbcTemplate.queryForObject(sql,
                Long.class,
                "iGRP Super Admin", SUPER_ADMIN_USERNAME, "%s@igrp.cv".formatted(SUPER_ADMIN_USERNAME),
                SYSTEM_USER, SYSTEM_USER);
    }

    private void assignRoleToSuperAdminUser(Long roleId, Long userId) {
        String sql = """
            INSERT INTO t_role_users
            (users_id, roles_id)
            SELECT ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM t_role_users WHERE users_id = ? AND roles_id = ?
            )
        """;
        jdbcTemplate.update(sql, userId, roleId, userId, roleId);

        LOGGER.info("[Startup Config] Superadmin user linked to role");
    }

    // =====================================================
    // Menu creation
    // =====================================================

    private void createDefaultMenus(Long appId) {
        long startTime = System.currentTimeMillis();
        try {
            InputStream jsonStream = new ClassPathResource("menus.json").getInputStream();
            JsonNode root = objectMapper.readTree(jsonStream);
            String currentJsonHash = String.valueOf(root.hashCode());

            // Fetch previous hash
            String sqlHash = """
                SELECT fields->>'menus_hash'
                FROM t_custom_field
                WHERE table_name='t_application' AND record_id=? LIMIT 1
            """;
            String previousHash = jdbcTemplate.query(sqlHash, ps -> ps.setLong(1, appId),
                    rs -> rs.next() ? rs.getString(1) : null);

            if (currentJsonHash.equals(previousHash)) {
                LOGGER.info("[Startup Config] Menu JSON unchanged. Skipping menu processing.");
                return;
            }

            // Delete old menus for the app
            jdbcTemplate.update("DELETE FROM t_menu_entry WHERE application_id = ?", appId);

            // Insert new menus recursively
            insertMenuHierarchy(root, null, (short) 0, appId);

            // Update hash in custom_field
            upsertCustomField(appId, "menus_hash", currentJsonHash);

            LOGGER.info("[Startup Config] Menu processing completed in {} ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOGGER.error("[Startup Config] Menu processing error: {}", e.getMessage(), e);
        }
    }

    private void insertMenuHierarchy(JsonNode node, Long parentId, short position, Long appId) {
        for (JsonNode entry : node) {
            try {
                MenuEntryType type = MenuEntryType.valueOf(entry.get("type").asText());

                Long menuId = jdbcTemplate.queryForObject("""
                    INSERT INTO t_menu_entry
                    (name, type, icon, position, target, url, page_slug, status,
                     parent_id, application_id,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, now(), ?, now())
                    RETURNING id
                """,
                        Long.class,
                        entry.get("name").asText(),
                        type.name(),
                        entry.has("icon") ? entry.get("icon").asText() : null,
                        position,
                        entry.has("target") ? entry.get("target").asText() : "_self",
                        entry.has("url") ? entry.get("url").asText() : null,
                        entry.has("pageSlug") ? entry.get("pageSlug").asText() : null,
                        parentId, appId,
                        SYSTEM_USER, SYSTEM_USER
                );

                if (entry.has("children")) {
                    insertMenuHierarchy(entry.get("children"), menuId, (short) 0, appId);
                }

                position++;
            } catch (Exception e) {
                LOGGER.warn("[Startup Config] Failed to process menu entry: {}", e.getMessage());
            }
        }
    }

    private void upsertCustomField(Long recordId, String key, String value) {
        String selectSql = """
            SELECT id FROM t_custom_field WHERE table_name='t_application' AND record_id=? LIMIT 1
        """;
        Long cfId = jdbcTemplate.query(selectSql, ps -> ps.setLong(1, recordId),
                rs -> rs.next() ? rs.getLong(1) : null);

        if (cfId == null) {
            jdbcTemplate.update("""
                INSERT INTO t_custom_field (table_name, record_id, fields,
                                            created_by, created_date, last_modified_by, last_modified_date)
                VALUES ('t_application', ?, jsonb_build_object(?, ?),
                        ?, now(), ?, now())
            """, recordId, key, value, SYSTEM_USER, SYSTEM_USER);
        } else {
            jdbcTemplate.update("""
                UPDATE t_custom_field
                SET fields = jsonb_set(fields, ?, ?::jsonb),
                    last_modified_by=?, last_modified_date=now()
                WHERE id = ?
            """, "{"+key+"}", "\""+value+"\"", SYSTEM_USER, cfId);
        }
    }

    // =====================================================
    // Helpers
    // =====================================================

    private boolean exists(String sql) {
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (EmptyResultDataAccessException e) {
            // This catch block is generally not needed if the SQL is COUNT(*),
            // as COUNT(*) will always return 0 if no rows match, not throw an exception.
            return false;
        }
    }

    private Long getId(String sql) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null);
    }
}
