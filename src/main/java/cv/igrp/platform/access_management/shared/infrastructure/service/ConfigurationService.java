package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class ConfigurationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationService.class);

    private final IGRPUserEntityRepository userRepository;
    private final ApplicationEntityRepository applicationRepository;
    private final DepartmentEntityRepository departmentRepository;
    private final MenuEntryEntityRepository menuEntryRepository;
    private final PermissionEntityRepository permissionRepository;
    private final RoleEntityRepository roleRepository;
    private final CustomFieldEntityRepository propertyRepository;
    private final ObjectMapper objectMapper;

    public ConfigurationService(
            IGRPUserEntityRepository userRepository,
            ApplicationEntityRepository applicationRepository,
            DepartmentEntityRepository departmentRepository,
            MenuEntryEntityRepository menuEntryRepository,
            PermissionEntityRepository permissionRepository,
            RoleEntityRepository roleRepository,
            CustomFieldEntityRepository propertyRepository,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.propertyRepository = propertyRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @Async
    public void initializeSystemConfiguration() {
        long startTime = System.currentTimeMillis();
        LOGGER.info("[Startup Config] Starting system initialization...");

        try {
            // 1. Bulk check existence of all default entities first
            boolean superAdminExists = userRepository.existsByUsername("superadmin");
            boolean departmentExists = departmentRepository.existsByCode("DEPT_IGRP");
            boolean appExists = applicationRepository.existsByType(AppType.SYSTEM);
            boolean permissionExists = permissionRepository.existsByName("manage_access");
            boolean roleExists = roleRepository.existsByName("superadmin");

            // 2. Create missing entities in optimized order
            DepartmentEntity department = departmentExists ?
                    departmentRepository.findByCode("DEPT_IGRP").get() :
                    createDefaultDepartment();

            ApplicationEntity app = appExists ?
                    applicationRepository.findFirstByType(AppType.SYSTEM).get() :
                    createDefaultApp(department);

            PermissionEntity permission = permissionExists ?
                    permissionRepository.findByName("manage_access").get() :
                    createDefaultPermission(app);

            RoleEntity role = roleExists ?
                    roleRepository.findByName("superadmin").get() :
                    createDefaultRole(department, permission);

            // 3. Handle user and role assignment
            IGRPUserEntity user = superAdminExists? userRepository.findByUsername("superadmin").get() : createSuperAdminUser();

            assignRoleToSuperAdminUser(role, user);

            // 4. Optimized menu handling
            createDefaultMenus(app);

            LOGGER.info("[Startup Config] System initialization completed in {} ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOGGER.error("[Startup Config] Error initializing system: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public DepartmentEntity createDefaultDepartment() {
        var newDept = new DepartmentEntity();
        newDept.setName("iGRP");
        newDept.setCode("DEPT_IGRP");
        newDept.setDescription("iGRP Department");
        var dept = departmentRepository.save(newDept);
        LOGGER.info("[Startup Config] Default Department created");
        return dept;
    }

    @Transactional
    public ApplicationEntity createDefaultApp(DepartmentEntity dept) {
        var newApp = new ApplicationEntity();
        newApp.setName("iGRP");
        newApp.setDescription("iGRP Application");
        newApp.setCode("APP_IGRP");
        newApp.setOwner("superadmin");
        newApp.setDepartmentId(dept);
        newApp.setStatus(Status.ACTIVE);
        newApp.setType(AppType.SYSTEM);
        var app = applicationRepository.save(newApp);
        LOGGER.info("[Startup Config] Default App created");
        return app;
    }

    @Transactional
    public PermissionEntity createDefaultPermission(ApplicationEntity app) {
        var newPerm = new PermissionEntity();
        newPerm.setName("manage_access");
        newPerm.setApplication(app);
        newPerm.setDescription("iGRP Manage Access Permission");
        newPerm.setStatus(Status.ACTIVE);
        var perm = permissionRepository.save(newPerm);
        LOGGER.info("[Startup Config] Default Permission created");
        return perm;
    }

    @Transactional
    public RoleEntity createDefaultRole(DepartmentEntity dept, PermissionEntity perm) {
        var newRole = new RoleEntity();
        newRole.setName("superadmin");
        newRole.setDepartment(dept);
        newRole.setDescription("iGRP Superadmin");
        newRole.setPermissions(Set.of(perm));
        newRole.setStatus(Status.ACTIVE);
        var role = roleRepository.save(newRole);
        LOGGER.info("[Startup Config] Default Role created");
        return role;
    }

    @Transactional
    public void assignRoleToSuperAdminUser(RoleEntity role, IGRPUserEntity user) {

        Set<IGRPUserEntity> users = role.getUsers();

        if (users == null) {
            users = new HashSet<>();
            role.setUsers(users);
        }
        boolean isRoleAdded = users.add(user);

        if (isRoleAdded) {
            LOGGER.info("Superadmin successfully added to role");
        } else {
            LOGGER.info("Superadmin was already associated with role");
        }

        roleRepository.save(role);

        LOGGER.info("[Startup Config] Superadmin user linked to role");
    }

    @Transactional
    public IGRPUserEntity createSuperAdminUser() {
        var newAdmin = new IGRPUserEntity();
        newAdmin.setName("iGRP Super Admin");
        newAdmin.setUsername("superadmin");
        newAdmin.setEmail("superadmin@igrp.cv");
        newAdmin.setRoles(new ArrayList<>());
        LOGGER.info("[Startup Config] Super admin user created");
        return userRepository.save(newAdmin);
    }

    @Transactional
    public void createDefaultMenus(ApplicationEntity app) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. First check if we even need to process menus
            JsonNode root = objectMapper.readTree(new ClassPathResource("menus.json").getInputStream());
            String currentJsonHash = String.valueOf(root.hashCode());

            // Check if we have a stored hash that matches
            Optional<CustomFieldEntity> menuHashProp = propertyRepository.findByTableNameAndRecordId(
                    "t_application", app.getId());

            if (menuHashProp.isPresent() && menuHashProp.get().getFields().getOrDefault("menus_hash", "<no_data>").equals(currentJsonHash)) {
                LOGGER.info("[Startup Config] Menu JSON unchanged. Skipping menu processing.");
                return;
            }

            // 2. Only proceed with full processing if JSON changed
            List<MenuEntryEntity> newMenus = new ArrayList<>();
            buildMenuHierarchy(root, null, (short) 0, app, newMenus);

            // 3. Fetch existing system menus with their hierarchy in one query
            List<MenuEntryEntity> existingSystemMenus = menuEntryRepository
                    .findSystemMenuHierarchy(app);

            // 4. Compare and update if needed
            if (!menusAreEqual(existingSystemMenus, newMenus)) {
                LOGGER.info("[Startup Config] Menu changes detected. Updating...");
                menuEntryRepository.deleteAll(existingSystemMenus);
                menuEntryRepository.saveAll(newMenus);

                Map<String, Object> fields;

                Optional<CustomFieldEntity> customFieldsOpt = propertyRepository.findByTableNameAndRecordId("t_application", app.getId());

                CustomFieldEntity customFields;

                if (customFieldsOpt.isPresent()) {
                    customFields = customFieldsOpt.get();
                    fields = customFields.getFields();
                } else {
                    fields = new HashMap<>();
                    customFields = new CustomFieldEntity();
                    customFields.setTableName("t_application");
                    customFields.setRecordId(app.getId());
                }

                fields.put("menus_hash", currentJsonHash);
                customFields.setFields(fields);

                // Store the new hash
                propertyRepository.save(customFields);
            }

            LOGGER.info("[Startup Config] Menu processing completed in {} ms",
                    System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LOGGER.error("[Startup Config] Menu processing error: {}", e.getMessage(), e);
        }
    }

    private void buildMenuHierarchy(JsonNode node, MenuEntryEntity parent, short position, ApplicationEntity app, List<MenuEntryEntity> accumulator) {
        for (JsonNode entry : node) {
            try {
                MenuEntryType type = MenuEntryType.valueOf(entry.get("type").asText());
                if (parent != null) {
                    MenuEntryType parentType = parent.getType();

                    if ((parentType == MenuEntryType.GROUP && type == MenuEntryType.GROUP) ||
                            (parentType == MenuEntryType.FOLDER && (type == MenuEntryType.FOLDER || type == MenuEntryType.GROUP)) ||
                            ((parentType == MenuEntryType.MENU_PAGE ||
                                    parentType == MenuEntryType.EXTERNAL_PAGE ||
                                    parentType == MenuEntryType.SYSTEM_PAGE))) {
                        LOGGER.warn("[Startup Config] Invalid hierarchy: skipping {} inside {}", type, parentType);
                        continue;
                    }
                }

                MenuEntryEntity menu = new MenuEntryEntity();
                menu.setName(entry.get("name").asText());
                menu.setType(type);
                menu.setIcon(entry.has("icon") ? entry.get("icon").asText() : null);
                menu.setPosition(position);
                menu.setTarget(entry.has("target") ? entry.get("target").asText() : "_self");
                menu.setUrl(entry.has("url") ? entry.get("url").asText() : null);
                menu.setPageSlug(entry.has("pageSlug") ? entry.get("pageSlug").asText() : null);
                menu.setStatus(Status.ACTIVE);
                menu.setParentId(parent);
                menu.setApplicationId(app);

                accumulator.add(menu);

                if (entry.has("children")) {
                    buildMenuHierarchy(entry.get("children"), menu, (short) 0, app, accumulator);
                }

                position++;

            } catch (Exception e) {
                LOGGER.warn("[Startup Config] Failed to process menu entry: {}", e.getMessage());
            }
        }
    }

    private boolean menusAreEqual(List<MenuEntryEntity> existingMenus, List<MenuEntryEntity> newMenus) {
        if (existingMenus.size() != newMenus.size()) {
            return false;
        }

        Map<String, MenuEntryEntity> existingMap = createMenuMap(existingMenus);
        Map<String, MenuEntryEntity> newMap = createMenuMap(newMenus);

        for (Map.Entry<String, MenuEntryEntity> entry : newMap.entrySet()) {
            MenuEntryEntity existing = existingMap.get(entry.getKey());
            MenuEntryEntity newMenu = entry.getValue();

            if (existing == null || !menuEntriesEqual(existing, newMenu)) {
                return false;
            }
        }

        return true;
    }

    private Map<String, MenuEntryEntity> createMenuMap(List<MenuEntryEntity> menus) {
        Map<String, MenuEntryEntity> map = new HashMap<>();
        for (MenuEntryEntity menu : menus) {
            String parentName = menu.getParentId() != null ? menu.getParentId().getName() : "null";
            String key = menu.getName() + "|" + menu.getType() + "|" + parentName;
            map.put(key, menu);
        }
        return map;
    }

    private boolean menuEntriesEqual(MenuEntryEntity a, MenuEntryEntity b) {
        return Objects.equals(a.getName(), b.getName()) &&
                a.getType() == b.getType() &&
                Objects.equals(a.getIcon(), b.getIcon()) &&
                Objects.equals(a.getUrl(), b.getUrl()) &&
                Objects.equals(a.getPageSlug(), b.getPageSlug()) &&
                Objects.equals(a.getTarget(), b.getTarget()) &&
                a.getStatus() == b.getStatus() &&
                (a.getParentId() == null && b.getParentId() == null ||
                        (a.getParentId() != null && b.getParentId() != null &&
                                Objects.equals(a.getParentId().getName(), b.getParentId().getName())));
    }
}