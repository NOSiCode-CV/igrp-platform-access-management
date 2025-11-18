package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${igrp.superadmin.user-external-id}")
    public String SUPER_ADMIN_EXTERNAL_ID = "";

    @Value("${igrp.superadmin.email}")
    private String SUPER_ADMIN_EMAIL = "superadmin@igrp.cv";

    public static final String IGRP_DEPARTMENT = "DEPT_IGRP";
    public static final String SUPER_ADMIN_ROLE = IGRP_DEPARTMENT + ".superadmin";
    public static final String IGRP_PERMISSION = IGRP_DEPARTMENT + ".manage_access";
    public static final String IGRP_RESOURCE = "igrp-access-management";
    private static final String IGRP_APP = "APP_IGRP_CENTER";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final IAdapter adapter;

    public ConfigurationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, IAdapter adapter) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.adapter = adapter;
    }

    @Transactional
    @Async(value = "igrpTaskExecutor")
    public void initializeSystemConfiguration() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[Startup Config] Starting system initialization...");

        if(SUPER_ADMIN_EXTERNAL_ID.isBlank()) {
            LOGGER.warn("[Startup Config] System admin external ID is not set. Skipping system initialization.");
            throw new RuntimeException("No superadmin user external ID provided. Please set IGRP_SUPERADMIN_USER_EXTERNAL_ID in your environment variables.");
        }

        try {
            // 1. Bulk existence checks in database
            boolean superAdminExists = exists("SELECT 1 FROM t_user WHERE external_id='%s' LIMIT 1".formatted(SUPER_ADMIN_EXTERNAL_ID));
            boolean departmentExists = exists("SELECT 1 FROM t_department WHERE code='%s' LIMIT 1".formatted(IGRP_DEPARTMENT));
            boolean appExists = exists("SELECT 1 FROM t_application WHERE type='SYSTEM' LIMIT 1");
            boolean permissionExists = exists("SELECT 1 FROM t_permission WHERE name='%s' LIMIT 1".formatted(IGRP_PERMISSION));
            boolean resourceExists = exists("SELECT 1 FROM t_resource WHERE name='%s' LIMIT 1".formatted(IGRP_RESOURCE));
            boolean roleExists = exists("SELECT 1 FROM t_role WHERE code='%s' LIMIT 1".formatted(SUPER_ADMIN_ROLE));

            // 2. Check provider existence before attempting sync
            boolean departmentExistsInProvider = checkAndCreateDepartment(departmentExists);
            //boolean appExistsInProvider = checkAndCreateApplication(appExists, departmentExistsInProvider);
            //boolean permissionExistsInProvider = checkAndCreatePermission(permissionExists, appExists);
            boolean roleExistsInProvider = checkAndCreateRole(roleExists, departmentExistsInProvider, true);

            // 3. Create missing entities in optimized order (only if provider sync was successful)
            Long departmentId = departmentExists ? getId("SELECT id FROM t_department WHERE code='%s'".formatted(IGRP_DEPARTMENT)) :
                    (departmentExistsInProvider ? createDefaultDepartmentInDB() : null);

            Long appId = appExists ? getId("SELECT id FROM t_application WHERE type='SYSTEM' LIMIT 1") :
                    createDefaultAppInDB(departmentId);

            Long permissionId = permissionExists ? getId("SELECT id FROM t_permission WHERE name='%s'".formatted(IGRP_PERMISSION)) :
                    createDefaultPermissionInDB(departmentId);

            Long resourceId = resourceExists? getId("SELECT id FROM t_resource WHERE name='%s'".formatted(IGRP_RESOURCE)) :
                    createDefaultResourceInDB(permissionId, appId);

            Long roleId = roleExists ? getId("SELECT id FROM t_role WHERE code='%s'".formatted(SUPER_ADMIN_ROLE)) :
                    (roleExistsInProvider ? createDefaultRoleInDB(departmentId, permissionId, appId) : null);

            Long userId = superAdminExists ? getId("SELECT id FROM t_user WHERE external_id='%s'".formatted(SUPER_ADMIN_EXTERNAL_ID)) :
                    createSuperAdminUserInDB();

            // 4. Assign role to superadmin user (only if all previous steps succeeded)
            if (roleExistsInProvider && userId != null && roleId != null) {
                assignRoleToSuperAdminUserInDB(roleId, userId);
            }

            // 5. Create default menus (independent of provider sync)
            if (appId != null) {
                createDefaultMenus(appId, roleId);
            }

            LOGGER.info("[Startup Config] System initialization completed in {} ms",
                    System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            LOGGER.error("[Startup Config] Error initializing system: {}", e.getMessage(), e);
        }
    }

    // =====================================================
    // Provider Existence Checks and Creation Methods
    // =====================================================

    /**
     * Checks if department exists in provider, creates if it doesn't exist in DB
     */
    private boolean checkAndCreateDepartment(boolean departmentExistsInDB) {
        try {
            boolean existsInProvider = adapter.departmentExists(IGRP_DEPARTMENT);

            if (!departmentExistsInDB && !existsInProvider) {
                LOGGER.info("[Startup Config] Creating department in provider: {}", IGRP_DEPARTMENT);
                adapter.createDepartment(IGRP_DEPARTMENT, null);
                return true;
            } else if (existsInProvider) {
                LOGGER.info("[Startup Config] Department exists in provider: {}", IGRP_DEPARTMENT);
                return true;
            } else {
                LOGGER.warn("[Startup Config] Department exists in DB but not in provider: {}", IGRP_DEPARTMENT);
                return false;
            }
        } catch (IAMException e) {
            LOGGER.error("[Startup Config] Failed to check/create department in provider: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if application exists in provider, creates if it doesn't exist in DB
     */
    private boolean checkAndCreateApplication(boolean appExistsInDB, boolean departmentExistsInProvider) {
        if (!departmentExistsInProvider) {
            LOGGER.warn("[Startup Config] Cannot create application, department does not exist in provider");
            return false;
        }

        try {
            boolean existsInProvider = adapter.applicationExists(IGRP_DEPARTMENT, IGRP_APP);

            if (!appExistsInDB && !existsInProvider) {
                LOGGER.info("[Startup Config] Creating application in provider: {}", IGRP_APP);
                adapter.createApplication(IGRP_DEPARTMENT, IGRP_APP);
                return true;
            } else if (existsInProvider) {
                LOGGER.info("[Startup Config] Application exists in provider: {}", IGRP_APP);
                return true;
            } else {
                LOGGER.warn("[Startup Config] Application exists in DB but not in provider: {}", IGRP_APP);
                return false;
            }
        } catch (IAMException e) {
            LOGGER.error("[Startup Config] Failed to check/create application in provider: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if permission exists in the provider, creates if it doesn't exist in DB
     */
    private boolean checkAndCreatePermission(boolean permissionExistsInDB, boolean appExistsInDB) {
        try {
            boolean existsInProvider = adapter.permissionExists(IGRP_PERMISSION);

            if (!permissionExistsInDB && !existsInProvider) {
                LOGGER.info("[Startup Config] Creating permission in provider: {}", IGRP_PERMISSION);
                adapter.createPermission(IGRP_PERMISSION, "iGRP Manage Access Permission");
                return true;
            } else if (existsInProvider) {
                LOGGER.info("[Startup Config] Permission exists in provider: {}", IGRP_PERMISSION);
                return true;
            } else {
                LOGGER.warn("[Startup Config] Permission exists in DB but not in provider: {}", IGRP_PERMISSION);
                return false;
            }
        } catch (IAMException e) {
            LOGGER.error("[Startup Config] Failed to check/create permission in provider: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Checks if a role exists in provider, creates if it doesn't exist in DB
     */
    private boolean checkAndCreateRole(boolean roleExistsInDB, boolean departmentExistsInProvider, boolean permissionExistsInProvider) {
        if (!departmentExistsInProvider) {
            LOGGER.warn("[Startup Config] Cannot create role, department does not exist in provider");
            return false;
        }

        try {
            boolean existsInProvider = adapter.roleExists(IGRP_DEPARTMENT, RoleValidator.normalizeRoleCodeForAdapter(SUPER_ADMIN_ROLE, IGRP_DEPARTMENT));

            if (!roleExistsInDB && !existsInProvider) {
                LOGGER.info("[Startup Config] Creating role in provider: {}", SUPER_ADMIN_ROLE);
                adapter.createRole(IGRP_DEPARTMENT, RoleValidator.normalizeRoleCodeForAdapter(SUPER_ADMIN_ROLE, IGRP_DEPARTMENT));

                // Assign permission to a role if permission exists
                //if (permissionExistsInProvider) {
                //    adapter.assignPermissionsToRole(Set.of(IGRP_PERMISSION), SUPER_ADMIN_ROLE);
                //}
                return true;
            } else if (existsInProvider) {
                LOGGER.info("[Startup Config] Role exists in provider: {}", SUPER_ADMIN_ROLE);
                return true;
            } else {
                LOGGER.warn("[Startup Config] Role exists in DB but not in provider: {}", SUPER_ADMIN_ROLE);
                return false;
            }
        } catch (IAMException e) {
            LOGGER.error("[Startup Config] Failed to check/create role in provider: {}", e.getMessage(), e);
            return false;
        }
    }

    // =====================================================
    // Database Entity Creation Methods (After Provider Sync)
    // =====================================================

    Long createDefaultDepartmentInDB() {
        String sql = """
                    INSERT INTO t_department
                    (name, code, description, status,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, ?, 'ACTIVE', ?, now(), ?, now())
                    RETURNING id
                """;
        var query = jdbcTemplate.queryForObject(sql,
                Long.class,
                "iGRP", IGRP_DEPARTMENT, "iGRP Department", SYSTEM_USER, SYSTEM_USER);
        LOGGER.info("[Startup Config] Default Department created in DB");
        return query;
    }

    Long createDefaultAppInDB(Long deptId) {
        String insertAppSql = """
        INSERT INTO t_application
            (name, code, description, owner, status, type,
             created_by, created_date, last_modified_by, last_modified_date)
        VALUES (?, ?, ?, ?, 'ACTIVE', 'SYSTEM', ?, now(), ?, now())
        RETURNING id
    """;

        // Step 1: Create application
        Long appId = jdbcTemplate.queryForObject(
                insertAppSql,
                Long.class,
                "iGRP App Center",
                IGRP_APP,
                "iGRP Application Center",
                1,
                SYSTEM_USER,
                SYSTEM_USER
        );

        if (appId != null) {
            // Step 2: Associate with department
            String insertRelationSql = """
            INSERT INTO t_department_application (department_id, application_id)
            VALUES (?, ?)
        """;
            jdbcTemplate.update(insertRelationSql, deptId, appId);

            LOGGER.info("[Startup Config] Default App {} associated with Department {}", appId, deptId);
        } else {
            LOGGER.warn("[Startup Config] Default App creation failed, no association with Department performed");
        }

        return appId;
    }

    Long createDefaultPermissionInDB(Long deptId) {
        String sql = """
                    INSERT INTO t_permission
                    (name, description, status, department,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, 'ACTIVE', ?, ?, now(), ?, now())
                    RETURNING id
                """;
        var query = jdbcTemplate.queryForObject(sql,
                Long.class,
                IGRP_PERMISSION, "iGRP Manage Access Permission", deptId,
                SYSTEM_USER, SYSTEM_USER);
        LOGGER.info("[Startup Config] Default Permission created in DB");
        return query;
    }

    Long createDefaultResourceInDB(Long permId, Long appId) {
        String sqlRole = """
                    INSERT INTO t_resource
                    (name, description, type, status,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, 'API', 'ACTIVE', ?, now(), ?, now())
                    RETURNING id
                """;
        Long resourceId = jdbcTemplate.queryForObject(sqlRole,
                Long.class,
                IGRP_RESOURCE, "iGRP Access Management API",
                SYSTEM_USER, SYSTEM_USER);

        // Step 1: Insert resource-permission relation
        String sqlRolePerm = """
                    INSERT INTO t_resource_permission
                    (resource_id, permission)
                    SELECT ?, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM t_resource_permission WHERE resource_id=? AND permission=?
                    )
                """;
        jdbcTemplate.update(sqlRolePerm, resourceId, permId, resourceId, permId);

        // Step 2: Associate with application
        String insertRelationSql = """
            INSERT INTO t_application_resource (resource_id, application_id)
            VALUES (?, ?)
        """;
        jdbcTemplate.update(insertRelationSql, resourceId, appId);

        LOGGER.info("[Startup Config] Default Resource created in DB");

        return resourceId;
    }

    Long createDefaultRoleInDB(Long deptId, Long permId, Long appId) {
        String sqlRole = """
                    INSERT INTO t_role
                    (code, name, description, status, department,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, ?, 'ACTIVE', ?, ?, now(), ?, now())
                    RETURNING id
                """;
        Long roleId = jdbcTemplate.queryForObject(sqlRole,
                Long.class,
                SUPER_ADMIN_ROLE, "iGRP Superadmin", "iGRP Superadmin", deptId,
                SYSTEM_USER, SYSTEM_USER);

        // Step 1: Insert role-permission relation
        String sqlRolePerm = """
                    INSERT INTO t_role_permission
                    (role_id, permission)
                    SELECT ?, ?
                    WHERE NOT EXISTS (
                        SELECT 1 FROM t_role_permission WHERE role_id=? AND permission=?
                    )
                """;
        jdbcTemplate.update(sqlRolePerm, roleId, permId, roleId, permId);

        // Step 2: Associate with application
        String insertRelationSql = """
            INSERT INTO t_application_role (roles, application_id)
            VALUES (?, ?)
        """;
        jdbcTemplate.update(insertRelationSql, roleId, appId);

        LOGGER.info("[Startup Config] Default Role created in DB");

        return roleId;
    }

    private Long createSuperAdminUserInDB() {
        String sql = """
                    INSERT INTO t_user
                    (name, external_id, email, status,
                     created_by, created_date, last_modified_by, last_modified_date)
                    VALUES (?, ?, ?, ?, ?, now(), ?, now())
                    RETURNING id
                """;
        LOGGER.info("[Startup Config] Super admin user created in DB");
        return jdbcTemplate.queryForObject(sql,
                Long.class,
                "iGRP Super Admin", SUPER_ADMIN_EXTERNAL_ID, SUPER_ADMIN_EMAIL, "ACTIVE",
                SYSTEM_USER, SYSTEM_USER);
    }

    void assignRoleToSuperAdminUserInDB(Long roleId, Long userId) {
        try {
            // Assign role in provider
            adapter.assignRoleToUser(IGRP_DEPARTMENT, RoleValidator.normalizeRoleCodeForAdapter(SUPER_ADMIN_ROLE, IGRP_DEPARTMENT), SUPER_ADMIN_EXTERNAL_ID);

            // Assign role in database
            String sql = """
                        INSERT INTO t_role_users
                        (users_id, roles_id)
                        SELECT ?, ?
                        WHERE NOT EXISTS (
                            SELECT 1 FROM t_role_users WHERE users_id = ? AND roles_id = ?
                        )
                    """;
            jdbcTemplate.update(sql, userId, roleId, userId, roleId);

            LOGGER.info("[Startup Config] Superadmin user linked to role in provider and DB");
        } catch (IAMException e) {
            LOGGER.error("[Startup Config] Failed to assign role to user in provider: {}", e.getMessage(), e);
        }
    }

    // =====================================================
    // Existing Menu Creation Methods (Unchanged)
    // =====================================================

    void createDefaultMenus(Long appId, Long roleId) {
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
            jdbcTemplate.update("UPDATE t_menu_entry SET status = 'DELETED' WHERE application_id = ?", appId);

            // Insert new menus recursively
            insertMenuHierarchy(root, null, (short) 0, appId, roleId);

            // Update hash in custom_field
            upsertCustomField(appId, "menus_hash", currentJsonHash);

            LOGGER.info("[Startup Config] Menu processing completed in {} ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOGGER.error("[Startup Config] Menu processing error: {}", e.getMessage(), e);
        }
    }

    private void insertMenuHierarchy(JsonNode node, Long parentId, short position, Long appId, Long roleId) {
        for (JsonNode entry : node) {
            try {
                MenuEntryType type = MenuEntryType.fromCodeOrThrow(entry.get("type").asText());

                Long menuId = jdbcTemplate.queryForObject("""
                                    INSERT INTO t_menu_entry
                                    (code, name, type, icon, position, target, url, page_slug, status,
                                     parent_id, application_id,
                                     created_by, created_date, last_modified_by, last_modified_date)
                                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, now(), ?, now())
                                    RETURNING id
                                """,
                        Long.class,
                        entry.get("code").asText(),
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

                // Assign menu to role
                if (roleId != null) {
                    jdbcTemplate.update("""
                                    INSERT INTO t_menu_entry_roles (roles_id, menu_entry_entity_id)
                                    SELECT ?, ?
                                    WHERE NOT EXISTS (
                                        SELECT 1 FROM t_menu_entry_roles WHERE roles_id=? AND menu_entry_entity_id=?
                                    )
                                """, roleId, menuId, roleId, menuId);
                }

                if (entry.has("children")) {
                    insertMenuHierarchy(entry.get("children"), menuId, (short) 0, appId, roleId);
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
                            SET fields = jsonb_set(fields, ?::text[], ?::jsonb),
                                last_modified_by=?, last_modified_date=now()
                            WHERE id = ?
                            """,
                    new Object[]{
                            "{" + key + "}",               // JSON path
                            "\"" + value + "\"",            // JSON value as string
                            SYSTEM_USER,
                            cfId
                    }
            );
        }
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    boolean exists(String sql) {
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    Long getId(String sql) {
        return jdbcTemplate.query(sql, rs -> rs.next() ? rs.getLong(1) : null);
    }
}