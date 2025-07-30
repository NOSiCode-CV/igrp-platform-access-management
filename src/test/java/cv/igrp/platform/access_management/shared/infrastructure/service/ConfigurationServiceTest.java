package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private JsonNode mockNode;

    @InjectMocks
    private ConfigurationService configurationService;

    @Test
    @DisplayName("initializeSystemConfiguration - should create all entities when none exist")
    void initializeSystem_createsAllEntitiesWhenNoneExist() throws Exception {
        // Setup - no entities exist
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenReturn(0); // for exists() checks

        // Mock the getId() calls
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(1L)  // department
                .thenReturn(2L)  // app
                .thenReturn(3L)  // permission
                .thenReturn(4L)  // role
                .thenReturn(5L); // user

        // Mock menu processing
        JsonNode mockMenuNode = mock(JsonNode.class);
        when(objectMapper.readTree(any(InputStream.class))).thenReturn(mockMenuNode);

        // Execute
        configurationService.initializeSystemConfiguration();

        // Verify
        verify(jdbcTemplate, atLeastOnce()).update(anyString(), any(Object[].class));
        verify(objectMapper).readTree(any(InputStream.class));
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should skip creation when entities exist")
    void initializeSystem_skipsCreationWhenEntitiesExist() {
        // Setup - all entities exist
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn("existing_hash"); // existing menu hash

        // Execute
        configurationService.initializeSystemConfiguration();

        // Verify - no creation calls
        verify(jdbcTemplate, never()).queryForObject(
                contains("INSERT"), eq(Long.class), any(Object[].class));
    }

    @Test
    @DisplayName("createDefaultDepartment - should insert with correct parameters")
    void createDefaultDepartment_insertsWithCorrectParams() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(10L);

        Long result = configurationService.createDefaultDepartment();

        assertEquals(10L, result);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO t_department"),
                eq(Long.class),
                eq("iGRP"), eq("DEPT_IGRP"), eq("iGRP Department"),
                eq("system"), eq("system")
        );
    }

    @Test
    @DisplayName("createDefaultApp - should insert with department relationship")
    void createDefaultApp_linksToDepartment() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(20L);

        Long result = configurationService.createDefaultApp(1L);

        assertEquals(20L, result);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO t_application"),
                eq(Long.class),
                eq("iGRP"), eq("APP_IGRP"), eq("iGRP Application"),
                eq("superadmin"), eq(1L), eq("system"), eq("system")
        );
    }

    @Test
    @DisplayName("createDefaultPermission - should link to application")
    void createDefaultPermission_linksToApplication() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(30L);

        Long result = configurationService.createDefaultPermission(1L);

        assertEquals(30L, result);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO t_permission"),
                eq(Long.class),
                eq("manage_access"), eq("iGRP Manage Access Permission"),
                eq(1L), eq("system"), eq("system")
        );
    }

    // TODO: check this unit test later
    @Test
    @Disabled
    @DisplayName("createDefaultRole - should create role and permission mapping")
    void createDefaultRole_createsRoleAndPermission() {
        // Clear any existing stubs that might interfere
        reset(jdbcTemplate);

        // 1. Stub the role creation with exact parameter matching
        String roleSql = """
        INSERT INTO t_role
        (name, description, status, department,
         created_by, created_date, last_modified_by, last_modified_date)
        VALUES (?, ?, 'ACTIVE', ?, ?, now(), ?, now())
        RETURNING id
        """.trim();

        when(jdbcTemplate.queryForObject(
                eq(roleSql),
                eq(Long.class),
                eq(anyString()),
                eq("iGRP Superadmin"),
                eq(1L),
                eq("system"),
                eq("system")
        )).thenReturn(40L);

        // 2. Stub the permission mapping with exact parameter matching
        String permSql = """
        INSERT INTO t_role_permission
        (role_id, permission)
        SELECT ?, ?
        WHERE NOT EXISTS (
            SELECT 1 FROM t_role_permission WHERE role_id=? AND permission=?
        )
        """.trim();

        // Use doReturn().when() pattern to avoid strict stubbing issues
        doReturn(1).when(jdbcTemplate).update(
                eq(permSql),
                eq(40L),
                eq(2L),
                eq(40L),
                eq(2L)
        );

        // Execute
        Long result = configurationService.createDefaultRole(1L, 2L);

        // Verify
        assertEquals(40L, result);

        // Verify role creation
        verify(jdbcTemplate).queryForObject(
                eq(roleSql),
                eq(Long.class),
                eq(anyString()),
                eq("iGRP Superadmin"),
                eq(1L),
                eq("system"),
                eq("system")
        );

        // Verify permission mapping
        verify(jdbcTemplate).update(
                eq(permSql),
                eq(40L),
                eq(2L),
                eq(40L),
                eq(2L)
        );
    }

    @Test
    @DisplayName("assignRoleToSuperAdminUser - should insert only if not exists")
    void assignRoleToSuperAdminUser_insertsIfNotExists() {
        configurationService.assignRoleToSuperAdminUser(5L, 10L);

        verify(jdbcTemplate).update(
                contains("INSERT INTO t_role_users"),
                eq(10L), eq(5L), eq(10L), eq(5L)
        );
    }

    @Test
    @DisplayName("createDefaultMenus - should process when hash differs")
    void createDefaultMenus_processesWhenHashDiffers() throws Exception {
        // Setup a realistic JsonNode for iteration
        // This ensures .elements() or .iterator() will not return null
            String jsonContent = "[{\"name\":\"Home\",\"url\":\"/home\",\"icon\":\"home-icon\",\"type\":\"EXTERNAL_PAGE\",\"order\":1,\"children\":[]}," +
                "{\"name\":\"Admin\",\"url\":\"/admin\",\"icon\":\"admin-icon\",\"type\":\"FOLDER\",\"order\":2,\"children\":[" +
                "{\"name\":\"Users\",\"url\":\"/admin/users\",\"icon\":\"user-icon\",\"type\":\"EXTERNAL_PAGE\",\"order\":1}" +
                "]}]";
        JsonNode realisticMenusNode = new ObjectMapper().readTree(new ByteArrayInputStream(jsonContent.getBytes()));
        when(objectMapper.readTree(any(InputStream.class))).thenReturn(realisticMenusNode);

        // Simulate no existing hash or a differing hash
        // The service likely uses queryForObject for a EXTERNAL_PAGE string result or
        // handles EmptyResultDataAccessException.
        // Let's assume queryForObject is used to get the hash.
        // When no hash is found (first run or different hash), it will throw EmptyResultDataAccessException
        // or return null. We'll simulate no hash found for the 'hash differs' scenario.
        when(jdbcTemplate.query(
                contains("SELECT fields->>'menus_hash'"), // Matches the SQL string
                any(PreparedStatementSetter.class),      // Matches the 'ps -> ps.setLong(1, appId)' lambda
                any(ResultSetExtractor.class)            // Matches the 'rs -> rs.next() ? rs.getString(1) : null' lambda
        )).thenReturn(null);

        // Stub for DELETE FROM t_menu_entry
        when(jdbcTemplate.update(contains("DELETE FROM t_menu_entry"), eq(1L)))
                .thenReturn(1);

        // Stub for INSERT INTO t_menu_entry (to return IDs for created menus)
        // This is called multiple times for each menu entry, so use atLeastOnce()
        when(jdbcTemplate.queryForObject(
                contains("INSERT INTO t_menu_entry"),
                eq(Long.class),
                any(Object[].class)) // Use any(Object[].class) as arguments will vary
        ).thenReturn(100L, 101L, 102L); // Return different IDs for each call

        // Stub for INSERT/UPDATE INTO t_custom_field (for saving the new hash)
        when(jdbcTemplate.update(contains("INSERT INTO t_custom_field"), any(Object[].class)))
                .thenReturn(1);


        // Execute the method
        configurationService.createDefaultMenus(1L);

        // Verifications
        // Verify that the hash was checked (which resulted in an exception or null)
        verify(jdbcTemplate).query(
                contains("SELECT fields->>'menus_hash'"), // Matches the SQL string
                any(PreparedStatementSetter.class),      // Matches the 'ps -> ps.setLong(1, appId)' lambda
                any(ResultSetExtractor.class)            // Matches the 'rs -> rs.next() ? rs.getString(1) : null' lambda
        );

        // Verify that existing menus were deleted for the given application ID
        verify(jdbcTemplate).update(contains("DELETE FROM t_menu_entry"), eq(1L));

        // Verify that menu entries were inserted (at least once for each menu item in the JSON)
        // We have 3 menu entries in the realisticMenusNode JSON.
        // Depending on how insertMenuHierarchy is implemented, it might call queryForObject
        // for each item and its children.
        verify(jdbcTemplate, atLeastOnce()) // Use atLeastOnce() because it's called repeatedly
                .queryForObject(contains("INSERT INTO t_menu_entry"), eq(Long.class), any(Object[].class));

        // Verify that the new hash was inserted into t_custom_field
        verify(jdbcTemplate).update(contains("INSERT INTO t_custom_field"), any(Object[].class));
    }

    @Test
    @DisplayName("createDefaultMenus - should skip when hash matches")
    void createDefaultMenus_skipsWhenHashMatches() throws Exception {
        when(objectMapper.readTree(any(InputStream.class))).thenReturn(mockNode);

        // Existing hash matches
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn("12345");

        configurationService.createDefaultMenus(1L);

        verify(jdbcTemplate, never()).update(contains("DELETE FROM t_menu_entry"), (PreparedStatementSetter) any());
        verify(jdbcTemplate, never())
                .queryForObject(contains("INSERT INTO t_menu_entry"), eq(Long.class), any());
    }

    @Test
    @DisplayName("createDefaultMenus - should handle JSON processing error")
    void createDefaultMenus_handlesJsonError() throws Exception {
        when(objectMapper.readTree(any(InputStream.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        assertDoesNotThrow(() -> configurationService.createDefaultMenus(1L));
        verify(jdbcTemplate, never()).update(contains("DELETE FROM t_menu_entry"), (PreparedStatementSetter) any());
    }

    @Test
    @DisplayName("exists - should return true when record exists")
    void exists_returnsTrueWhenRecordExists() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        assertTrue(configurationService.exists("SELECT 1 FROM table"));
    }

    @Test
    @DisplayName("exists - should return false when no records")
    void exists_returnsFalseWhenNoRecords() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        assertFalse(configurationService.exists("SELECT 1 FROM table"));
    }

    @Test
    @DisplayName("getId - should return ID when record exists")
    void getId_returnsIdWhenExists() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(100L);
        assertEquals(100L, configurationService.getId("SELECT id FROM table"));
    }

    @Test
    @DisplayName("getId - should return null when no record")
    void getId_returnsNullWhenNotExists() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class)))
                .thenReturn(null);
        assertNull(configurationService.getId("SELECT id FROM table"));
    }
}