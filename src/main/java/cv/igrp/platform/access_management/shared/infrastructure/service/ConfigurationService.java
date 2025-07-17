package cv.igrp.platform.access_management.shared.infrastructure.service;

import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
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
    private final ObjectMapper objectMapper;

    public ConfigurationService(
            IGRPUserEntityRepository userRepository,
            ApplicationEntityRepository applicationRepository,
            DepartmentEntityRepository departmentRepository,
            MenuEntryEntityRepository menuEntryRepository,
            PermissionEntityRepository permissionRepository,
            RoleEntityRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.departmentRepository = departmentRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional(dontRollbackOn = Exception.class)
    public void initializeSystemConfiguration() {
        try {
            createSuperAdminUser();

            var department = createDefaultDepartment();
            var app = createDefaultApp(department);
            var permission = createDefaultPermission(app);
            var role = createDefaultRole(department, permission);
            assignRoleToSuperAdminUser(role);

            createDefaultMenus(app);

        } catch (Exception e) {
            LOGGER.error("[Startup Config] Error initializing system: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public DepartmentEntity createDefaultDepartment() {
        return departmentRepository.findByCode("DEPT_IGRP").orElseGet(() -> {
            var newDept = new DepartmentEntity();
            newDept.setName("iGRP");
            newDept.setCode("DEPT_IGRP");
            newDept.setDescription("iGRP Department");
            var dept = departmentRepository.save(newDept);
            LOGGER.info("[Startup Config] Default Department created");
            return dept;
        });
    }

    @Transactional
    public ApplicationEntity createDefaultApp(DepartmentEntity dept) {
        return applicationRepository.findFirstByType(AppType.SYSTEM).orElseGet(() -> {
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
        });
    }

    @Transactional
    public PermissionEntity createDefaultPermission(ApplicationEntity app) {
        return permissionRepository.findByName("manage_access").orElseGet(() -> {
            var newPerm = new PermissionEntity();
            newPerm.setName("manage_access");
            newPerm.setApplication(app);
            newPerm.setDescription("iGRP Manage Access Permission");
            newPerm.setStatus(Status.ACTIVE);
            var perm = permissionRepository.save(newPerm);
            LOGGER.info("[Startup Config] Default Permission created");
            return perm;
        });
    }

    @Transactional
    public RoleEntity createDefaultRole(DepartmentEntity dept, PermissionEntity perm) {
        return roleRepository.findByName("superadmin").orElseGet(() -> {
            var newRole = new RoleEntity();
            newRole.setName("superadmin");
            newRole.setDepartment(dept);
            newRole.setDescription("iGRP Superadmin");
            newRole.setPermissions(Set.of(perm));
            newRole.setStatus(Status.ACTIVE);
            var role = roleRepository.save(newRole);
            LOGGER.info("[Startup Config] Default Role created");
            return role;
        });
    }

    @Transactional
    public void assignRoleToSuperAdminUser(RoleEntity role) {
        var adminOpt = userRepository.findByUsername("superadmin");
        if (adminOpt.isPresent()) {
            var admin = adminOpt.get();
            if (admin.getRoles() == null) {
                admin.setRoles(new ArrayList<>());
            }
            if (!admin.getRoles().contains(role)) {
                admin.getRoles().add(role);
                userRepository.save(admin);
                LOGGER.info("[Startup Config] Superadmin user linked to role");
            }
        } else {
            LOGGER.warn("[Startup Config] Superadmin user not found when assigning role");
        }
    }

    @Transactional
    public void createSuperAdminUser() {
        var admin = userRepository.findByUsername("superadmin");
        if (admin.isEmpty()) {
            var newAdmin = new IGRPUserEntity();
            newAdmin.setName("iGRP Super Admin");
            newAdmin.setUsername("superadmin");
            newAdmin.setEmail("superadmin@igrp.cv");
            newAdmin.setRoles(new ArrayList<>());
            userRepository.save(newAdmin);
            LOGGER.info("[Startup Config] Super admin user created");
        } else {
            LOGGER.info("[Startup Config] Super admin user already exists");
        }
    }

    @Transactional
    public void createDefaultMenus(ApplicationEntity app) {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("menus.json").getInputStream());
            List<MenuEntryEntity> newMenus = new ArrayList<>();
            buildMenuHierarchy(root, null, (short) 0, app, newMenus);
            // Step 1: Fetch existing SYSTEM menus for this app
            List<MenuEntryEntity> systemMenus = menuEntryRepository.findByApplicationIdAndType(app, MenuEntryType.SYSTEM_PAGE);

            // Step 2: Optionally include their parent menus if needed (to avoid orphans)
            Set<MenuEntryEntity> menusToDelete = new HashSet<>(systemMenus);
            for (MenuEntryEntity menu : systemMenus) {
                collectParentMenus(menu, menusToDelete);
            }

            // Step 3: Delete only collected system menus and parents
            menuEntryRepository.deleteAll(menusToDelete);
            menuEntryRepository.saveAll(newMenus);
            LOGGER.info("[Startup Config] System menus created successfully.");
        } catch (IOException e) {
            LOGGER.error("[Startup Config] Failed to parse menu JSON: {}", e.getMessage());
        } catch (Exception ex) {
            LOGGER.error("[Startup Config] Unexpected error while creating menus: {}", ex.getMessage());
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

    /**
     * Recursively collects all parent menus of a given menu.
     */
    private void collectParentMenus(MenuEntryEntity menu, Set<MenuEntryEntity> result) {
        MenuEntryEntity parent = menu.getParentId();
        if (parent != null && result.add(parent)) {
            collectParentMenus(parent, result);
        }
    }

}