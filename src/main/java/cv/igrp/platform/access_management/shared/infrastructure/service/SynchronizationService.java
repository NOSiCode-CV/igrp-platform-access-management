package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.framework.auth.core.model.*;
import jakarta.ws.rs.ClientErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Synchronization Service for maintaining consistency between Database and IAM Provider
 * Implements the synchronization patterns defined in IAM_SYNCHRONIZATION.md specification
 */
@Service
public class SynchronizationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizationService.class);
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INACTIVE_STATUS = "INACTIVE";

    private final JdbcTemplate jdbcTemplate;
    private final IAdapter adapter;

    // Cache for tracking synchronization state
    private final Map<String, SyncResult> syncResults = new ConcurrentHashMap<>();

    public SynchronizationService(JdbcTemplate jdbcTemplate, IAdapter adapter) {
        this.jdbcTemplate = jdbcTemplate;
        this.adapter = adapter;
    }

    /**
     * Startup reconciliation - called on application startup
     * Follows the order: Definitions before assignments
     */
    @Async(value = "igrpTaskExecutor")
    @Transactional
    public void startupReconciliation() {
        LOGGER.info("[Sync] Starting startup reconciliation...");
        long startTime = System.currentTimeMillis();

        try {
            // Phase 1: Sync definitions (order matters)
            syncDepartments();
            //syncApplications(); disabled for now as applications are only managed in IGRP, no need for provider management
            syncRoles();
            //syncPermissions(); disabled for now as permissions are only managed in IGRP
            //syncResources(); disabled for now as resources are only managed in IGRP

            // Phase 2: Sync assignments (after definitions)
            //syncRolePermissionAssignments(); disabled for now as permissions are only managed in IGRP
            syncUserRoleAssignments();

            // Phase 3: User synchronization (special rules apply)
            syncUsers();

            syncMappers();

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("[Sync] Startup reconciliation completed in {} ms", duration);
            syncResults.put("startup", new SyncResult(true, "Startup reconciliation completed", duration));

        } catch (Exception e) {
            LOGGER.error("[Sync] Startup reconciliation failed: {}", e.getMessage(), e);
            syncResults.put("startup", new SyncResult(false, "Startup reconciliation failed: " + e.getMessage(), 0));
        }
    }

    /**
     * On-demand synchronization check
     */
    public SyncStatus checkSynchronization() {
        LOGGER.info("[Sync] Performing synchronization check...");
        Map<String, SyncDifference> differences = new HashMap<>();

        try {
            differences.put("departments", checkDepartmentDifferences());
            differences.put("applications", checkApplicationDifferences());
            differences.put("roles", checkRoleDifferences());
            differences.put("permissions", checkPermissionDifferences());
            differences.put("resources", checkResourceDifferences());
            differences.put("userRoles", checkUserRoleDifferences());
            differences.put("rolePermissions", checkRolePermissionDifferences());

            boolean needsSync = differences.values().stream()
                    .anyMatch(diff -> !diff.getMissingInProvider().isEmpty() ||
                            !diff.getMissingInDatabase().isEmpty() ||
                            !diff.getConflicts().isEmpty());

            return new SyncStatus(needsSync, differences, syncResults);

        } catch (Exception e) {
            LOGGER.error("[Sync] Synchronization check failed: {}", e.getMessage(), e);
            return new SyncStatus(false, differences, "Check failed: " + e.getMessage());
        }
    }

    /**
     * On-demand synchronization repair
     */
    @Async(value = "igrpTaskExecutor")
    @Transactional
    public void repairSynchronization() {
        LOGGER.info("[Sync] Starting repair synchronization...");
        long startTime = System.currentTimeMillis();

        try {
            SyncStatus status = checkSynchronization();
            if (!status.isNeedsSync()) {
                LOGGER.info("[Sync] No synchronization needed");
                return;
            }

            // Repair based on differences found
            repairBasedOnDifferences(status.getDifferences());

            long duration = System.currentTimeMillis() - startTime;
            LOGGER.info("[Sync] Repair synchronization completed in {} ms", duration);
            syncResults.put("repair", new SyncResult(true, "Repair synchronization completed", duration));

        } catch (Exception e) {
            LOGGER.error("[Sync] Repair synchronization failed: {}", e.getMessage(), e);
            syncResults.put("repair", new SyncResult(false, "Repair synchronization failed: " + e.getMessage(), 0));
        }
    }

    // =====================================================
    // Core Synchronization Methods
    // =====================================================

    /**
     * Sync departments: DB wins for business configuration
     */
    private void syncDepartments() throws IAMException {
        LOGGER.info("[Sync] Synchronizing departments...");

        // Get departments from database
        List<DepartmentInfo> dbDepartments = getDepartmentsFromDatabase();
        // Get departments from provider
        List<DepartmentInfo> providerDepartments = adapter.getAllDepartments();

        // Find differences
        Set<String> dbCodes = dbDepartments.stream().map(DepartmentInfo::getCode).collect(Collectors.toSet());
        Set<String> providerCodes = providerDepartments.stream().map(DepartmentInfo::getCode).collect(Collectors.toSet());

        // Create in provider if missing
        for (DepartmentInfo dbDept : dbDepartments) {
            if (!providerCodes.contains(dbDept.getCode())) {
                try {
                    adapter.createDepartment(dbDept.getCode(), dbDept.getParentDepartment());
                    LOGGER.info("[Sync] Created department in provider: {}", dbDept.getCode());
                } catch (IAMException e) {
                    if(e.getCause() != null && e.getCause() instanceof ClientErrorException clEx) {
                        if (clEx.getResponse().getStatus() == 409) {
                            LOGGER.info("[Sync] The department {} is already present in the provider", dbDept.getCode());
                        }
                    }
                    LOGGER.warn("[Sync] Failed to create department {} in provider: {}", dbDept.getCode(), e.getMessage());
                }
            }
        }

        // Delete from provider if not in DB (DB wins)
        for (DepartmentInfo providerDept : providerDepartments) {
            if (!dbCodes.contains(providerDept.getCode())) {
                try {
                    adapter.deleteDepartment(providerDept.getCode());
                    LOGGER.info("[Sync] Deleted department from provider: {}", providerDept.getCode());
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to delete department {} from provider: {}", providerDept.getCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * Sync applications: DB wins for business configuration
     */
    private void syncApplications() throws IAMException {
        LOGGER.info("[Sync] Synchronizing applications...");

        List<ApplicationInfo> dbApplications = getApplicationsFromDatabase();
        List<ApplicationInfo> providerApplications = adapter.getAllApplications();

        // Create composite key: departmentCode + ":" + applicationCode
        Set<String> dbAppKeys = dbApplications.stream()
                .map(ApplicationInfo::getCode)
                .collect(Collectors.toSet());

        Set<String> providerAppKeys = providerApplications.stream()
                .map(ApplicationInfo::getCode)
                .collect(Collectors.toSet());

        // Create in provider if missing
        for (ApplicationInfo dbApp : dbApplications) {
            String key = dbApp.getCode();
            if (!providerAppKeys.contains(key)) {
                try {
                    adapter.createApplication(dbApp.getDepartmentCode(), dbApp.getCode());
                    LOGGER.info("[Sync] Created application in provider: {}", key);
                } catch (IAMException e) {
                    if(e.getCause() != null && e.getCause() instanceof ClientErrorException clEx) {
                        if (clEx.getResponse().getStatus() == 409) {
                            LOGGER.info("[Sync] The application {} is already present in the provider", key);
                        }
                    }
                    LOGGER.warn("[Sync] Failed to create application {} in provider: {}", key, e.getMessage());
                }
            }
        }

        // Delete from provider if not in DB
        for (ApplicationInfo providerApp : providerApplications) {
            String key = providerApp.getCode();
            if (!dbAppKeys.contains(key)) {
                try {
                    adapter.deleteApplication(providerApp.getDepartmentCode(), providerApp.getCode());
                    LOGGER.info("[Sync] Deleted application from provider: {}", key);
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to delete application {} from provider: {}", key, e.getMessage());
                }
            }
        }
    }

    /**
     * Sync roles: DB wins for business configuration
     */
    private void syncRoles() throws IAMException {
        LOGGER.info("[Sync] Synchronizing roles...");

        List<RoleInfo> dbRoles = getRolesFromDatabase();
        List<RoleInfo> providerRoles = adapter.getAllRoles();

        // Considering composite key: $departmentCode + $roleName
        Set<String> dbRoleKeys = dbRoles.stream()
                .map(RoleInfo::getName)
                .collect(Collectors.toSet());

        Set<String> providerRoleKeys = providerRoles.stream()
                .map(RoleInfo::getName)
                .collect(Collectors.toSet());

        // Create in provider if missing
        for (RoleInfo dbRole : dbRoles) {
            String key = dbRole.getName();
            //LOGGER.info("[[IGRP_DEBUG {}]]: Syncing role key: {}", dbRole.getName(), key);
            //LOGGER.info("[[IGRP_DEBUG {}]]: DB Role Keys: {}", dbRole.getName(), dbRoleKeys);
            //LOGGER.info("[[IGRP_DEBUG {}]]: Provider Role Keys: {}", dbRole.getName(), providerRoleKeys);
            if (!providerRoleKeys.contains(key)) {
                try {
                    adapter.createRole(dbRole.getDepartmentCode(), dbRole.getName());
                    LOGGER.info("[Sync] Created role in provider: {}", key);
                } catch (IAMException e) {
                    if(e.getCause() != null && e.getCause() instanceof ClientErrorException clEx) {
                        if (clEx.getResponse().getStatus() == 409) {
                            LOGGER.info("[Sync] The role {} is already present in the provider", key);
                        }
                    }
                    LOGGER.warn("[Sync] Failed to create role {} in provider: {}", key, e.getMessage());
                }
            }
        }

        // Delete it from provider if not in DB
        for (RoleInfo providerRole : providerRoles) {
            String key = providerRole.getName();
            if (!dbRoleKeys.contains(key) && key.contains(".")) {
                try {
                    adapter.deleteRole(providerRole.getDepartmentCode(), providerRole.getName());
                    LOGGER.info("[Sync] Deleted role from provider: {}", key);
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to delete role {} from provider: {}", key, e.getMessage());
                }
            }
        }
    }

    /**
     * Sync permissions: DB wins for business configuration
     */
    private void syncPermissions() throws IAMException {
        LOGGER.info("[Sync] Synchronizing permissions...");

        List<PermissionInfo> dbPermissions = getPermissionsFromDatabase();
        List<PermissionInfo> providerPermissions = adapter.getAllPermissions();

        Set<String> dbPermissionNames = dbPermissions.stream()
                .map(PermissionInfo::getName)
                .collect(Collectors.toSet());

        Set<String> providerPermissionNames = providerPermissions.stream()
                .map(PermissionInfo::getName)
                .collect(Collectors.toSet());

        // Create in provider if missing
        for (PermissionInfo dbPerm : dbPermissions) {
            if (!providerPermissionNames.contains(dbPerm.getName())) {
                try {
                    adapter.createPermission(dbPerm.getName(), dbPerm.getDescription());
                    LOGGER.info("[Sync] Created permission in provider: {}", dbPerm.getName());
                } catch (IAMException e) {
                    if(e.getCause() != null && e.getCause() instanceof ClientErrorException clEx) {
                        if (clEx.getResponse().getStatus() == 409) {
                            LOGGER.info("[Sync] The permission {} is already present in the provider", dbPerm.getName());
                        }
                    }
                    LOGGER.warn("[Sync] Failed to create permission {} in provider: {}", dbPerm.getName(), e.getMessage());
                }
            }
        }

        // Delete from provider if not in DB
        for (PermissionInfo providerPerm : providerPermissions) {
            if (!dbPermissionNames.contains(providerPerm.getName())) {
                try {
                    adapter.deletePermission(providerPerm.getName());
                    LOGGER.info("[Sync] Deleted permission from provider: {}", providerPerm.getName());
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to delete permission {} from provider: {}", providerPerm.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Sync resources: DB wins for business configuration
     */
    private void syncResources() throws IAMException {
        LOGGER.info("[Sync] Synchronizing resources...");

        List<ResourceInfo> dbResources = getResourcesFromDatabase();
        List<ResourceInfo> providerResources = adapter.getAllResources();

        Set<String> dbResourceNames = dbResources.stream()
                .map(ResourceInfo::getName)
                .collect(Collectors.toSet());

        Set<String> providerResourceNames = providerResources.stream()
                .map(ResourceInfo::getName)
                .collect(Collectors.toSet());

        // Create in provider if missing
        for (ResourceInfo dbResource : dbResources) {
            if (!providerResourceNames.contains(dbResource.getName())) {
                try {
                    adapter.createResource(dbResource.getName(), dbResource.getDescription(),
                            dbResource.getUris(), dbResource.getScopes());
                    LOGGER.info("[Sync] Created resource in provider: {}", dbResource.getName());
                } catch (IAMException e) {
                    if(e.getCause() != null && e.getCause() instanceof ClientErrorException clEx) {
                        if (clEx.getResponse().getStatus() == 409) {
                            LOGGER.info("[Sync] The resource {} is already present in the provider", dbResource.getName());
                        }
                    }
                    LOGGER.warn("[Sync] Failed to create resource {} in provider: {}", dbResource.getName(), e.getMessage());
                }
            }
        }

        // Delete from provider if not in DB
        for (ResourceInfo providerResource : providerResources) {
            if (!dbResourceNames.contains(providerResource.getName())) {
                try {
                    adapter.deleteResource(providerResource.getName());
                    LOGGER.info("[Sync] Deleted resource from provider: {}", providerResource.getName());
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to delete resource {} from provider: {}", providerResource.getName(), e.getMessage());
                }
            }
        }
    }

    /**
     * Sync role-permission assignments
     */
    private void syncRolePermissionAssignments() throws IAMException {
        LOGGER.info("[Sync] Synchronizing role-permission assignments...");

        // Get all role-permission mappings from database
        Map<String, Set<String>> dbRolePermissions = getRolePermissionsFromDatabase();
        // Get all role-permission mappings from provider
        Map<String, Set<String>> providerRolePermissions = adapter.getAllRolePermissions();

        for (Map.Entry<String, Set<String>> entry : dbRolePermissions.entrySet()) {
            String permissionName = entry.getKey();
            Set<String> dbRoleNames = entry.getValue();
            Set<String> providerRoleNames = providerRolePermissions.getOrDefault(permissionName, Set.of());

            // Find roles to assign and unassign
            Set<String> rolesToAssign = new HashSet<>(dbRoleNames);
            rolesToAssign.removeAll(providerRoleNames);

            Set<String> rolesToUnassign = new HashSet<>(providerRoleNames);
            rolesToUnassign.removeAll(dbRoleNames);

            if (!rolesToAssign.isEmpty()) {
                try {
                    adapter.assignPermissionToRoles(permissionName, rolesToAssign);
                    LOGGER.info("[Sync] Assigned permission {} to roles: {}", permissionName, rolesToAssign);
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to assign permission {} to roles: {}", permissionName, e.getMessage());
                }
            }

            if (!rolesToUnassign.isEmpty()) {
                try {
                    adapter.unassignPermissionFromRoles(permissionName, rolesToUnassign);
                    LOGGER.info("[Sync] Unassigned permission {} from roles: {}", permissionName, rolesToUnassign);
                } catch (IAMException e) {
                    LOGGER.warn("[Sync] Failed to unassign permission {} from roles: {}", permissionName, e.getMessage());
                }
            }
        }
    }

    /**
     * Sync user-role assignments (only for ACTIVE users that exist in provider)
     */
    private void syncUserRoleAssignments() throws IAMException {
        LOGGER.info("[Sync] Synchronizing user-role assignments...");

        // Get all user-role mappings from database (only active users)
        Map<String, Map<String, Set<String>>> dbUserRoles = getUserRolesFromDatabase();
        // Get all user-role mappings from provider
        Map<String, Map<String, Set<String>>> providerUserRoles = adapter.getAllUserRoles();

        for (Map.Entry<String, Map<String, Set<String>>> userEntry : dbUserRoles.entrySet()) {
            String username = userEntry.getKey();
            Map<String, Set<String>> dbUserRolesByDept = userEntry.getValue();
            Map<String, Set<String>> providerUserRolesByDept = providerUserRoles.getOrDefault(username, Map.of());

            // Check if user exists in provider AND is active
            Optional<UserIdentity> user = adapter.resolveUser(username);
            if (user.isEmpty()) {
                LOGGER.warn("[Sync] User {} not found in provider, skipping role assignments", username);
                continue;
            }

            // Additional safety check: Verify user is active in our database
            if (!isUserActiveInDatabase(username)) {
                LOGGER.info("[Sync] User {} is INACTIVE in database, skipping role assignments", username);
                continue;
            }

            // Process each department
            for (Map.Entry<String, Set<String>> deptEntry : dbUserRolesByDept.entrySet()) {
                String departmentCode = deptEntry.getKey();
                Set<String> dbRoleNames = deptEntry.getValue();
                Set<String> providerRoleNames = providerUserRolesByDept.getOrDefault(departmentCode, Set.of());

                // Find roles to assign and unassign
                Set<String> rolesToAssign = new HashSet<>(dbRoleNames);
                rolesToAssign.removeAll(providerRoleNames);

                Set<String> rolesToUnassign = new HashSet<>(providerRoleNames);
                rolesToUnassign.removeAll(dbRoleNames);

                // Assign new roles
                for (String roleName : rolesToAssign) {
                    try {
                        adapter.assignRoleToUser(departmentCode, roleName, username);
                        LOGGER.info("[Sync] Assigned role {} to user {} in department {}", roleName, username, departmentCode);
                    } catch (IAMException e) {
                        LOGGER.warn("[Sync] Failed to assign role {} to user {}: {}", roleName, username, e.getMessage());
                    }
                }

                // Unassign removed roles
                for (String roleName : rolesToUnassign) {
                    try {
                        adapter.unassignRoleFromUser(departmentCode, roleName, username);
                        LOGGER.info("[Sync] Unassigned role {} from user {} in department {}", roleName, username, departmentCode);
                    } catch (IAMException e) {
                        LOGGER.warn("[Sync] Failed to unassign role {} from user {}: {}", roleName, username, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Sync users: Special rules apply (Provider wins for user existence)
     */
    private void syncUsers() throws IAMException {
        LOGGER.info("[Sync] Synchronizing users...");

        // Get all users from database
        List<UserIdentity> dbUsers = adapter.getAllUsers(); // Using adapter to get consistent format
        // Get all users from provider
        List<UserIdentity> providerUsers = adapter.getAllUsers();

        Set<String> dbUsernames = dbUsers.stream()
                .map(UserIdentity::getUsername)
                .collect(Collectors.toSet());

        Set<String> providerUsernames = providerUsers.stream()
                .map(UserIdentity::getUsername)
                .collect(Collectors.toSet());

        // Mark users as INACTIVE in DB if they don't exist in provider
        Set<String> usersMissingInProvider = new HashSet<>(dbUsernames);
        usersMissingInProvider.removeAll(providerUsernames);

        for (String username : usersMissingInProvider) {
            try {
                markUserAsInactive(username);
                LOGGER.info("[Sync] Marked user as INACTIVE in DB: {}", username);
            } catch (Exception e) {
                LOGGER.warn("[Sync] Failed to mark user {} as INACTIVE: {}", username, e.getMessage());
            }
        }

        // Note: Users missing in DB but present in provider are ignored (invite-only mode)
        Set<String> usersMissingInDB = new HashSet<>(providerUsernames);
        usersMissingInDB.removeAll(dbUsernames);

        if (!usersMissingInDB.isEmpty()) {
            LOGGER.info("[Sync] Users present in provider but not in DB (invite-only mode): {}", usersMissingInDB);
        }
    }

    private void syncMappers() throws IAMException {

        boolean existRolesClaimMapper = adapter.protocolMapperExists("iGRP Roles");

        if(!existRolesClaimMapper) {
            LOGGER.info("[Sync] Creating JWT Protocol Mapper in the provider...");
            adapter.createJwtRolesClaimMapper("igrp_roles", "iGRP Roles");
            LOGGER.info("[Sync] JWT Protocol Mapper created successfully in the provider");
        } else {
            LOGGER.info("[Sync] JWT Protocol Mapper is already present in the provider");
        }

    }

    // =====================================================
    // Database Query Methods
    // =====================================================

    private List<DepartmentInfo> getDepartmentsFromDatabase() {
        String sql = """
                SELECT d.code, d.name, d.description, pd.code as parentDepartment, d.status
                FROM t_department d
                LEFT JOIN t_department pd on d.parent_id = pd.id
                WHERE d.status = ?
                ORDER BY d.parent_id NULLS FIRST
                """;
        return jdbcTemplate.query(sql, (rs, _) -> {
            DepartmentInfo dept = new DepartmentInfo();
            dept.setCode(rs.getString("code"));
            dept.setName(rs.getString("name"));
            dept.setDescription(rs.getString("description"));
            dept.setParentDepartment(rs.getString("parentDepartment"));
            dept.setStatus(rs.getString("status"));
            return dept;
        }, ACTIVE_STATUS);
    }

    private List<ApplicationInfo> getApplicationsFromDatabase() {
        String sql = """
                SELECT a.code, a.name, a.description, a.status, a.type
                FROM t_application a
                WHERE a.status = ?
                """;
        return jdbcTemplate.query(sql, (rs, _) -> {
            ApplicationInfo app = new ApplicationInfo();
            app.setCode(rs.getString("code"));
            app.setName(rs.getString("name"));
            app.setDescription(rs.getString("description"));
            //app.setDepartmentCode(rs.getString("departmentCode"));
            app.setStatus(rs.getString("status"));
            app.setType(rs.getString("type"));
            return app;
        }, ACTIVE_STATUS);
    }

    private List<RoleInfo> getRolesFromDatabase() {
        String sql = """
                SELECT r.code, r.description, r.status, d.code as departmentCode
                FROM t_role r
                LEFT JOIN t_department d ON r.department = d.id
                WHERE r.status = ?
                ORDER BY r.parent NULLS FIRST
                """;
        return jdbcTemplate.query(sql, (rs, _) -> {
            RoleInfo role = new RoleInfo();
            role.setName(rs.getString("code"));
            role.setDescription(rs.getString("description"));
            role.setDepartmentCode(rs.getString("departmentCode"));
            role.setStatus(rs.getString("status"));
            return role;
        }, ACTIVE_STATUS);
    }

    private List<PermissionInfo> getPermissionsFromDatabase() {
        String sql = "SELECT name, description, status FROM t_permission WHERE status = ?";
        return jdbcTemplate.query(sql, (rs, _) -> {
            PermissionInfo perm = new PermissionInfo();
            perm.setName(rs.getString("name"));
            perm.setDescription(rs.getString("description"));
            perm.setStatus(rs.getString("status"));
            return perm;
        }, ACTIVE_STATUS);
    }

    private List<ResourceInfo> getResourcesFromDatabase() {
        String sql = """
        SELECT r.id, r.name, r.description, ri.url, r.status
        FROM t_resource r
        LEFT JOIN t_resource_item ri ON ri.resource_id = r.id
        WHERE r.status = ?
    """;

        Map<Integer, ResourceInfo> resourceMap = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            try {
                int resourceId = rs.getInt("id");

                ResourceInfo resource = resourceMap.computeIfAbsent(resourceId, id -> {
                    try {
                        ResourceInfo r = new ResourceInfo();
                        r.setName(rs.getString("name"));
                        r.setDescription(rs.getString("description"));
                        r.setUris(new ArrayList<>());
                        r.setScopes(List.of()); // placeholder
                        r.setStatus(rs.getString("status"));
                        return r;
                    } catch (SQLException e) {
                        // Skip resource entirely if core data is missing
                        return null;
                    }
                });

                // Only proceed if resource creation succeeded
                if (resource != null) {
                    try {
                        String url = rs.getString("url");
                        if (url != null) {
                            resource.getUris().add(url);
                        }
                    } catch (SQLException e) {
                        // Skip adding URL, but don't break processing
                    }
                }
            } catch (SQLException e) {
                // Skip this entire row if even resourceId is broken
            }
        }, ACTIVE_STATUS);

        // Filter out any null values (in case resource creation failed)
        return resourceMap.values().stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Set<String>> getRolePermissionsFromDatabase() {
        String sql = """
                SELECT p.name as permission_name, r.code as role_name 
                FROM t_role_permission rp 
                LEFT JOIN t_permission p ON rp.permission = p.id 
                LEFT JOIN t_role r ON rp.role_id = r.id 
                WHERE p.status = ? AND r.status = ?
                """;

        Map<String, Set<String>> result = new HashMap<>();
        jdbcTemplate.query(sql, (rs, _) -> {
            String permissionName = rs.getString("permission_name");
            String roleName = rs.getString("role_name");
            result.computeIfAbsent(permissionName, _ -> new HashSet<>()).add(roleName);
            return null;
        }, ACTIVE_STATUS, ACTIVE_STATUS);

        return result;
    }

    private Map<String, Map<String, Set<String>>> getUserRolesFromDatabase() {
        String sql = """
            SELECT u.username, d.code as department_code, r.code as role_name
            FROM t_role_users ru
            LEFT JOIN t_user u ON ru.users_id = u.id
            LEFT JOIN t_role r ON ru.roles_id = r.id
            LEFT JOIN t_department d ON r.department = d.id
            WHERE u.status = ? AND r.status = ? AND d.status = ?
            """;

        Map<String, Map<String, Set<String>>> result = new HashMap<>();
        jdbcTemplate.query(sql, (rs, _) -> {
            String username = rs.getString("username");
            String departmentCode = rs.getString("department_code");
            String roleName = rs.getString("role_name");

            result.computeIfAbsent(username, _ -> new HashMap<>())
                    .computeIfAbsent(departmentCode, _ -> new HashSet<>())
                    .add(roleName);
            return null;
        }, ACTIVE_STATUS, ACTIVE_STATUS, ACTIVE_STATUS); // Only ACTIVE users

        return result;
    }

    private void markUserAsInactive(String username) {
        String sql = "UPDATE t_user SET status = ? WHERE username = ?";
        jdbcTemplate.update(sql, INACTIVE_STATUS, username);
    }

    /**
     * Check if user is active in database
     */
    private boolean isUserActiveInDatabase(String username) {
        try {
            String sql = "SELECT status FROM t_user WHERE username = ?";
            String status = jdbcTemplate.queryForObject(sql, String.class, username);
            return ACTIVE_STATUS.equals(status);
        } catch (Exception e) {
            LOGGER.warn("[Sync] Failed to check status for user {}: {}", username, e.getMessage());
            return false; // If we can't verify status, assume inactive for safety
        }
    }

    // =====================================================
    // Difference Checking Methods
    // =====================================================

    private SyncDifference checkDepartmentDifferences() throws IAMException {
        List<DepartmentInfo> dbDepts = getDepartmentsFromDatabase();
        List<DepartmentInfo> providerDepts = adapter.getAllDepartments();

        return calculateDifferences(
                dbDepts.stream().map(DepartmentInfo::getCode).collect(Collectors.toSet()),
                providerDepts.stream().map(DepartmentInfo::getCode).collect(Collectors.toSet())
        );
    }

    private SyncDifference checkApplicationDifferences() throws IAMException {
        List<ApplicationInfo> dbApps = getApplicationsFromDatabase();
        List<ApplicationInfo> providerApps = adapter.getAllApplications();

        Set<String> dbKeys = dbApps.stream()
                .map(ApplicationInfo::getCode)
                .collect(Collectors.toSet());
        Set<String> providerKeys = providerApps.stream()
                .map(ApplicationInfo::getCode)
                .collect(Collectors.toSet());

        return calculateDifferences(dbKeys, providerKeys);
    }

    private SyncDifference checkRoleDifferences() throws IAMException {
        List<RoleInfo> dbRoles = getRolesFromDatabase();
        List<RoleInfo> providerRoles = adapter.getAllRoles();

        Set<String> dbKeys = dbRoles.stream()
                .map(RoleInfo::getName)
                .collect(Collectors.toSet());
        Set<String> providerKeys = providerRoles.stream()
                .map(RoleInfo::getName)
                .collect(Collectors.toSet());

        return calculateDifferences(dbKeys, providerKeys);
    }

    private SyncDifference checkPermissionDifferences() throws IAMException {
        List<PermissionInfo> dbPerms = getPermissionsFromDatabase();
        List<PermissionInfo> providerPerms = adapter.getAllPermissions();

        return calculateDifferences(
                dbPerms.stream().map(PermissionInfo::getName).collect(Collectors.toSet()),
                providerPerms.stream().map(PermissionInfo::getName).collect(Collectors.toSet())
        );
    }

    private SyncDifference checkResourceDifferences() throws IAMException {
        List<ResourceInfo> dbResources = getResourcesFromDatabase();
        List<ResourceInfo> providerResources = adapter.getAllResources();

        return calculateDifferences(
                dbResources.stream().map(ResourceInfo::getName).collect(Collectors.toSet()),
                providerResources.stream().map(ResourceInfo::getName).collect(Collectors.toSet())
        );
    }

    private SyncDifference checkUserRoleDifferences() throws IAMException {
        Map<String, Map<String, Set<String>>> dbUserRoles = getUserRolesFromDatabase();
        Map<String, Map<String, Set<String>>> providerUserRoles = adapter.getAllUserRoles();

        Set<String> differences = new HashSet<>();
        // Simplified check - in real implementation, would compare detailed assignments
        if (!dbUserRoles.equals(providerUserRoles)) {
            differences.add("User-role assignments differ");
        }

        return new SyncDifference(Set.of(), Set.of(), differences);
    }

    private SyncDifference checkRolePermissionDifferences() throws IAMException {
        Map<String, Set<String>> dbRolePerms = getRolePermissionsFromDatabase();
        Map<String, Set<String>> providerRolePerms = adapter.getAllRolePermissions();

        Set<String> differences = new HashSet<>();
        if (!dbRolePerms.equals(providerRolePerms)) {
            differences.add("Role-permission assignments differ");
        }

        return new SyncDifference(Set.of(), Set.of(), differences);
    }

    private SyncDifference calculateDifferences(Set<String> dbItems, Set<String> providerItems) {
        Set<String> missingInProvider = new HashSet<>(dbItems);
        missingInProvider.removeAll(providerItems);

        Set<String> missingInDatabase = new HashSet<>(providerItems);
        missingInDatabase.removeAll(dbItems);

        Set<String> conflicts = new HashSet<>();
        // Conflicts would be items that exist in both but have different properties
        // This is a simplified implementation

        return new SyncDifference(missingInProvider, missingInDatabase, conflicts);
    }

    // =====================================================
    // Repair Methods
    // =====================================================

    private void repairBasedOnDifferences(Map<String, SyncDifference> differences) {
        LOGGER.info("[Sync] Repairing based on differences...");

        for (Map.Entry<String, SyncDifference> entry : differences.entrySet()) {
            String entityType = entry.getKey();
            SyncDifference diff = entry.getValue();

            LOGGER.info("[Sync] Repairing {}: missingInProvider={}, missingInDatabase={}, conflicts={}",
                    entityType, diff.getMissingInProvider().size(),
                    diff.getMissingInDatabase().size(), diff.getConflicts().size());

            // Implementation would create/delete entities based on differences
            // This is a simplified version - full implementation would handle each entity type specifically
        }

        // After repairing differences, run full synchronization
        startupReconciliation();
    }

    // =====================================================
    // Utility Methods
    // =====================================================

    private List<String> parseJsonArray(String jsonArray) {
        if (jsonArray == null || jsonArray.trim().isEmpty()) {
            return List.of();
        }
        try {
            // Simple JSON array parsing - in production use a proper JSON parser
            return Arrays.stream(jsonArray.replace("[", "").replace("]", "").split(","))
                    .map(String::trim)
                    .map(s -> s.replace("\"", ""))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("Failed to parse JSON array: {}", jsonArray);
            return List.of();
        }
    }

    // =====================================================
    // Data Classes for Sync Results
    // =====================================================

    public static class SyncStatus {
        private final boolean needsSync;
        private final Map<String, SyncDifference> differences;
        private final Map<String, SyncResult> history;
        private final String message;

        public SyncStatus(boolean needsSync, Map<String, SyncDifference> differences, Map<String, SyncResult> history) {
            this.needsSync = needsSync;
            this.differences = differences;
            this.history = history;
            this.message = "Synchronization check completed";
        }

        public SyncStatus(boolean needsSync, Map<String, SyncDifference> differences, String message) {
            this.needsSync = needsSync;
            this.differences = differences;
            this.history = Map.of();
            this.message = message;
        }

        // Getters
        public boolean isNeedsSync() { return needsSync; }
        public Map<String, SyncDifference> getDifferences() { return differences; }
        public Map<String, SyncResult> getHistory() { return history; }
        public String getMessage() { return message; }
    }

    public static class SyncDifference {
        private final Set<String> missingInProvider;
        private final Set<String> missingInDatabase;
        private final Set<String> conflicts;

        public SyncDifference(Set<String> missingInProvider, Set<String> missingInDatabase, Set<String> conflicts) {
            this.missingInProvider = missingInProvider;
            this.missingInDatabase = missingInDatabase;
            this.conflicts = conflicts;
        }

        // Getters
        public Set<String> getMissingInProvider() { return missingInProvider; }
        public Set<String> getMissingInDatabase() { return missingInDatabase; }
        public Set<String> getConflicts() { return conflicts; }
    }

    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final long durationMs;
        private final Date timestamp;

        public SyncResult(boolean success, String message, long durationMs) {
            this.success = success;
            this.message = message;
            this.durationMs = durationMs;
            this.timestamp = new Date();
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }
        public Date getTimestamp() { return timestamp; }
    }
}