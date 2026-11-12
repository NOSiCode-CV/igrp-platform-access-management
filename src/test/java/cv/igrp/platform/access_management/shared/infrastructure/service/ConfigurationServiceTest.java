package cv.igrp.platform.access_management.shared.infrastructure.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    private static final String IGRP_DEPARTMENT = "DEPT_IGRP";
    private static final String SUPER_ADMIN_ROLE = "DEPT_IGRP.superadmin";
    private static final String IGRP_PERMISSION = "DEPT_IGRP.manage_access";
    private static final String IGRP_RESOURCE = "igrp-access-management";
    private static final String IGRP_APP = "APP_IGRP_CENTER";
    private static final String SUPER_ADMIN_EXTERNAL_ID = "f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454";
    private static final String SUPER_ADMIN_UUID = "0ab33988-489d-440a-b99d-5ff0aab21262";

    private JdbcTemplate jdbcTemplate;
    private JsonMapper objectMapper;
    private ConfigurationService configurationService;
    private RootUser rootUser;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = mock(JsonMapper.class);

        rootUser = mock(RootUser.class);
        configurationService = new ConfigurationService(jdbcTemplate, objectMapper, rootUser);
        configurationService.SUPER_ADMIN_EXTERNAL_ID = SUPER_ADMIN_EXTERNAL_ID;
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should create all entities when none exist in DB")
    void initializeSystem_createsAllEntitiesWhenNoneExist() throws Exception {
        // Force DB to report empty
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

        // Mock DB inserts to avoid NPEs (non-user PKs remain Long)
        doReturn(1L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), any(Object[].class));
        doReturn(2L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_application"), eq(Long.class), any(Object[].class));
        doReturn(3L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_permission"), eq(Long.class), any(Object[].class));
        doReturn(4L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_role"), eq(Long.class), any(Object[].class));
        doReturn(6L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_resource"), eq(Long.class), any(Object[].class));
        doReturn(SUPER_ADMIN_UUID).when(rootUser).sync(any(), any());

        // Mock menus processing
        String jsonContent = "[{\"code\": \"HOME\", \"name\":\"Home\",\"url\": \"/home\",\"icon\":\"home-icon\",\"type\":\"EXTERNAL_PAGE\",\"children\":[]} ]";
        JsonNode menusNode = new JsonMapper().readTree(jsonContent);
        doReturn(menusNode).when(objectMapper).readTree(any(InputStream.class));
        doReturn(null).when(jdbcTemplate).query(contains("SELECT fields->>'menus_hash'"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        // Execute the method under test
        configurationService.initializeSystemConfiguration();

        // The root user is RootUser's (#20): ConfigurationService writes no t_user row itself.
        verify(rootUser).sync(any(), eq(SUPER_ADMIN_EXTERNAL_ID));
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO t_user"), any(Class.class), any(Object[].class));
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should skip creation when entities already exist in DB")
    void initializeSystem_skipsCreationWhenEntitiesExist() throws Exception {
        // Force DB to report entities exist
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

        // SELECT id FROM ... (other tables) goes through getId (rs.getLong)
        doAnswer(inv -> {
            ResultSetExtractor<Long> extractor = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getLong(1)).thenReturn(99L);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(argThat((String s) -> s != null && s.startsWith("SELECT id FROM ") && !s.contains("t_user")), any(ResultSetExtractor.class));

        // Execute the method under test
        configurationService.initializeSystemConfiguration();

        // Verify no DB insertions occurred
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO"), eq(String.class), any(Object[].class));
    }

    @Test
    @DisplayName("initializeSystemConfiguration - the root user comes from RootUser, by the configured email (#20)")
    void initializeSystem_syncsRootByEmail() {
        ReflectionTestUtils.setField(configurationService, "superAdminEmail", "root@example.cv");

        configurationService.initializeSystemConfiguration();

        verify(rootUser).sync("root@example.cv", SUPER_ADMIN_EXTERNAL_ID);
    }

    @Test
    @DisplayName("AM refuses to start without IGRP_SUPERADMIN_EMAIL (#20)")
    void requiresTheRootEmail() {
        assertThrows(IllegalStateException.class, () -> ConfigurationService.requireRootEmail(""));
        assertThrows(IllegalStateException.class, () -> ConfigurationService.requireRootEmail(null));
        assertThrows(IllegalStateException.class, () -> ConfigurationService.requireRootEmail("not-an-email"));
        assertDoesNotThrow(() -> ConfigurationService.requireRootEmail("root@example.cv"));
    }
}
