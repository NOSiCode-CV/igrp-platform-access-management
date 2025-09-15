package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
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
    @Mock private IAdapter adapter;

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
    void createDefaultDepartment_insertsWithCorrectParams() throws IAMException {
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
                eq("iGRP App Center"), eq("APP_IGRP_CENTER"), eq("iGRP Application Center"),
                eq("superadmin"), eq(1L), eq("system"), eq("system")
        );
    }

    @Test
    @DisplayName("createDefaultPermission - should link to application")
    void createDefaultPermission_linksToApplication() throws IAMException {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(30L);

        Long result = configurationService.createDefaultPermission(1L, 1L);

        assertEquals(30L, result);
        verify(jdbcTemplate).queryForObject(
                contains("INSERT INTO t_permission"),
                eq(Long.class),
                eq("DEPT_IGRP.manage_access"), eq("iGRP Manage Access Permission"),
                eq(1L), eq(1L), eq("system"), eq("system")
        );
    }

    // TODO: check this unit test later
    @Test
    @Disabled
    @DisplayName("createDefaultRole - should create role and permission mapping")
    void createDefaultRole_createsRoleAndPermission() throws IAMException {
        // Clear any existing stubs that might interfere
        reset(jdbcTemplate);

        // 1. Stub the role creation (use startsWith to ignore formatting/whitespace)
        when(jdbcTemplate.queryForObject(
                startsWith("INSERT INTO t_role"),
                eq(Long.class),
                eq("DEPT_IGRP.superadmin"),
                eq("iGRP Superadmin"),
                eq(1L),
                eq("system"),
                eq("system")
        )).thenReturn(40L);

        // 2. Stub the permission mapping
        doReturn(1).when(jdbcTemplate).update(
                startsWith("INSERT INTO t_role_permission"),
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
                startsWith("INSERT INTO t_role"),
                eq(Long.class),
                anyString(),
                eq("iGRP Superadmin"),
                eq(1L),
                eq("system"),
                eq("system")
        );

        // Verify permission mapping
        verify(jdbcTemplate).update(
                startsWith("INSERT INTO t_role_permission"),
                eq(40L),
                eq(2L),
                eq(40L),
                eq(2L)
        );
    }

    @Test
    @DisplayName("assignRoleToSuperAdminUser - should insert only if not exists")
    void assignRoleToSuperAdminUser_insertsIfNotExists() throws IAMException {
        configurationService.assignRoleToSuperAdminUser(5L, 10L);

        verify(jdbcTemplate).update(
                contains("INSERT INTO t_role_users"),
                eq(10L), eq(5L), eq(10L), eq(5L)
        );
    }

    // TODO: verify this unit test
    @Test
    @Disabled
    @DisplayName("createDefaultMenus - should process when hash differs")
    void createDefaultMenus_processesWhenHashDiffers() throws Exception {
        String jsonContent = """
    [
      {"code": "HOME", "name":"Home","url":"/home","icon":"home-icon","type":"EXTERNAL_PAGE","children":[]},
      {"code": "ADMIN", "name":"Admin","url":"/admin","icon":"admin-icon","type":"FOLDER",
       "children":[{"code": "USERS", "name":"Users","url":"/admin/users","icon":"user-icon","type":"EXTERNAL_PAGE"}]}
    ]
    """;
        JsonNode realisticMenusNode = new ObjectMapper().readTree(jsonContent);
        when(objectMapper.readTree(any(InputStream.class))).thenReturn(realisticMenusNode);

        // Simulate no previous hash found
        when(jdbcTemplate.query(
                contains("SELECT fields->>'menus_hash'"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
        )).thenReturn(null);

        // Stub DELETE old menus
        when(jdbcTemplate.update(startsWith("DELETE FROM t_menu_entry"), eq(1L)))
                .thenReturn(1);

        // ✅ Correct stubbing for varargs
        lenient().when(jdbcTemplate.queryForObject(
                startsWith("INSERT INTO t_menu_entry"),
                eq(Long.class),
                Mockito.<Object[]>any()
        )).thenReturn(100L, 101L, 102L);

        lenient().when(jdbcTemplate.queryForObject(
                startsWith("INSERT INTO t_menu_entry"),
                eq(Long.class),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(100L, 101L, 102L);


        // Execute method
        configurationService.createDefaultMenus(1L);

        // Verify hash check
        verify(jdbcTemplate).query(
                contains("SELECT fields->>'menus_hash'"),
                any(PreparedStatementSetter.class),
                any(ResultSetExtractor.class)
        );

        // Verify old menu deletion
        verify(jdbcTemplate).update(startsWith("DELETE FROM t_menu_entry"), eq(1L));

        // Verify menu entries inserted (2 top-level + 1 child)
        verify(jdbcTemplate, atLeast(3))
                .queryForObject(startsWith("INSERT INTO t_menu_entry"),
                        eq(Long.class),
                        any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // Verify custom field hash insertion
        verify(jdbcTemplate).update(startsWith("INSERT INTO t_custom_field"), Mockito.<Object[]>any());
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