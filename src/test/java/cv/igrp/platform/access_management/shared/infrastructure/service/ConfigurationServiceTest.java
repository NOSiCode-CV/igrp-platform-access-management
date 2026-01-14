package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.platform.access_management.role.domain.service.RoleValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.io.InputStream;
import java.util.Set;

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

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private IAdapter adapter;
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        adapter = mock(IAdapter.class);

        configurationService = new ConfigurationService(jdbcTemplate, objectMapper, adapter);
        configurationService.SUPER_ADMIN_EXTERNAL_ID = SUPER_ADMIN_EXTERNAL_ID;
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should create all entities when none exist in DB and Provider")
    void initializeSystem_createsAllEntitiesWhenNoneExist() throws Exception {
        // Force DB to report empty
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_user"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

        // Ensure provider reports department and role do NOT exist
        when(adapter.departmentExists(ConfigurationServiceTest.IGRP_DEPARTMENT)).thenReturn(false);
        String normalizedRole = RoleValidator.normalizeRoleCodeForAdapter(SUPER_ADMIN_ROLE, IGRP_DEPARTMENT);
        when(adapter.roleExists(ConfigurationServiceTest.IGRP_DEPARTMENT, normalizedRole)).thenReturn(false);

        // Stub provider creation calls
        doNothing().when(adapter).createDepartment(ConfigurationServiceTest.IGRP_DEPARTMENT, null);
        doNothing().when(adapter).createRole(ConfigurationServiceTest.IGRP_DEPARTMENT, normalizedRole);
        doNothing().when(adapter).assignRoleToUser(ConfigurationServiceTest.IGRP_DEPARTMENT, normalizedRole, ConfigurationServiceTest.SUPER_ADMIN_EXTERNAL_ID);

        // Mock DB inserts to avoid NPEs
        doReturn(1L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), any(Object[].class));
        doReturn(2L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_application"), eq(Long.class), any(Object[].class));
        doReturn(3L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_permission"), eq(Long.class), any(Object[].class));
        doReturn(4L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_role"), eq(Long.class), any(Object[].class));
        doReturn(5L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_user"), eq(Long.class), any(Object[].class));
        doReturn(6L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_resource"), eq(Long.class), any(Object[].class));

        // Mock menus processing
        String jsonContent = "[{\"code\": \"HOME\", \"name\":\"Home\",\"url\": \"/home\",\"icon\":\"home-icon\",\"type\":\"EXTERNAL_PAGE\",\"children\":[]} ]";
        JsonNode menusNode = new ObjectMapper().readTree(jsonContent);
        doReturn(menusNode).when(objectMapper).readTree(any(InputStream.class));
        doReturn(null).when(jdbcTemplate).query(contains("SELECT fields->>'menus_hash'"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        // Execute the method under test
        configurationService.initializeSystemConfiguration();

        // Verify provider interactions
        verify(adapter).createDepartment(ConfigurationServiceTest.IGRP_DEPARTMENT, null);
        verify(adapter).createRole(ConfigurationServiceTest.IGRP_DEPARTMENT, normalizedRole);
        verify(adapter).assignRoleToUser(ConfigurationServiceTest.IGRP_DEPARTMENT, normalizedRole, ConfigurationServiceTest.SUPER_ADMIN_EXTERNAL_ID);

        // Verify DB insertions occurred
        verify(jdbcTemplate, atLeast(6)).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should skip creation when entities exist in both DB and Provider")
    void initializeSystem_skipsCreationWhenEntitiesExist() throws Exception {
        doReturn(1).when(jdbcTemplate).queryForObject(anyString(), eq(Integer.class));

        doReturn(true).when(adapter).departmentExists(IGRP_DEPARTMENT);
        String normalizedRole = RoleValidator.normalizeRoleCodeForAdapter(SUPER_ADMIN_ROLE, IGRP_DEPARTMENT);
        doReturn(true).when(adapter).roleExists(IGRP_DEPARTMENT, normalizedRole);

        // Mock DB IDs
        doReturn(1L).when(jdbcTemplate).query(contains("SELECT id FROM t_department"), any(ResultSetExtractor.class));
        doReturn(2L).when(jdbcTemplate).query(contains("SELECT id FROM t_application"), any(ResultSetExtractor.class));
        doReturn(3L).when(jdbcTemplate).query(contains("SELECT id FROM t_permission"), any(ResultSetExtractor.class));
        doReturn(4L).when(jdbcTemplate).query(contains("SELECT id FROM t_role"), any(ResultSetExtractor.class));
        doReturn(5L).when(jdbcTemplate).query(contains("SELECT id FROM t_user"), any(ResultSetExtractor.class));

        // Mock menu hash
        String jsonContent = """
                [
                  {"code": "HOME", "name":"Home","url":"/home","icon":"home-icon","type":"EXTERNAL_PAGE","children":[]}
                ]
                """;
        JsonNode menusNode = new ObjectMapper().readTree(jsonContent);
        doReturn(menusNode).when(objectMapper).readTree(any(InputStream.class));
        doReturn(String.valueOf(menusNode.hashCode())).when(jdbcTemplate).query(
                contains("SELECT fields->>'menus_hash'"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        configurationService.initializeSystemConfiguration();

        verify(adapter, never()).createDepartment(anyString(), anyString());
        verify(adapter, never()).createRole(anyString(), anyString());
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }

    @Test
    @DisplayName("checkAndCreateDepartment - creates department when not exists")
    void checkAndCreateDepartment_createsWhenNotExists() throws Exception {
        doReturn(0).when(jdbcTemplate).queryForObject(anyString(), eq(Integer.class));
        when(adapter.departmentExists(IGRP_DEPARTMENT)).thenReturn(false);
        doNothing().when(adapter).createDepartment(IGRP_DEPARTMENT, null);

        configurationService.initializeSystemConfiguration();

        verify(adapter).createDepartment(IGRP_DEPARTMENT, null);
    }

    @Test
    @DisplayName("checkAndCreateDepartment - skips when exists")
    void checkAndCreateDepartment_skipsWhenExists() throws Exception {
        doReturn(0).when(jdbcTemplate).queryForObject(anyString(), eq(Integer.class));
        when(adapter.departmentExists(IGRP_DEPARTMENT)).thenReturn(true);

        configurationService.initializeSystemConfiguration();

        verify(adapter, never()).createDepartment(anyString(), anyString());
    }

    @Test
    @DisplayName("createDefaultDepartmentInDB - inserts with correct parameters")
    void createDefaultDepartmentInDB_insertsWithCorrectParams() throws IAMException {
        doReturn(0).when(jdbcTemplate).queryForObject(anyString(), eq(Integer.class));
        doReturn(false).when(adapter).departmentExists(anyString());
        doReturn(1L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), any(Object[].class));

        configurationService.initializeSystemConfiguration();

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), paramsCaptor.capture());

        Object[] params = paramsCaptor.getValue();
        assertEquals("iGRP", params[0]);
        assertEquals("DEPT_IGRP", params[1]);
        assertEquals("iGRP Department", params[2]);
        assertEquals("system", params[3]);
        assertEquals("system", params[4]);
    }

    @Test
    @DisplayName("createDefaultAppInDB - inserts and links to department")
    void createDefaultAppInDB_linksToDepartment() throws IAMException {
        doReturn(0).when(jdbcTemplate).queryForObject(anyString(), eq(Integer.class));
        doReturn(false).when(adapter).departmentExists(anyString());
        doReturn(1L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), any(Object[].class));
        doReturn(2L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_application"), eq(Long.class), any(Object[].class));

        configurationService.initializeSystemConfiguration();

        ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(contains("INSERT INTO t_application"), eq(Long.class), paramsCaptor.capture());

        Object[] params = paramsCaptor.getValue();
        assertEquals("iGRP App Center", params[0]);
        assertEquals("APP_IGRP_CENTER", params[1]);
        assertEquals("iGRP Application Center", params[2]);
        assertEquals(1, params[3]);
        assertEquals("system", params[4]);
    }
}