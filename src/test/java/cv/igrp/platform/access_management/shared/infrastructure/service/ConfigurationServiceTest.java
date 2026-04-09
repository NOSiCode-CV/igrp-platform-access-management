package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = mock(ObjectMapper.class);

        configurationService = new ConfigurationService(jdbcTemplate, objectMapper);
        configurationService.SUPER_ADMIN_EXTERNAL_ID = SUPER_ADMIN_EXTERNAL_ID;
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should create all entities when none exist in DB")
    void initializeSystem_createsAllEntitiesWhenNoneExist() throws Exception {
        // Force DB to report empty
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_user"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(0).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

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

        // Verify no provider interactions (no-adapter architecture)
        // Verify DB insertions occurred
        verify(jdbcTemplate, atLeast(6)).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }

    @Test
    @DisplayName("initializeSystemConfiguration - should skip creation when entities already exist in DB")
    void initializeSystem_skipsCreationWhenEntitiesExist() throws Exception {
        // Force DB to report entities exist
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_user"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

        // Execute the method under test
        configurationService.initializeSystemConfiguration();

        // Verify no provider interactions (no-adapter architecture)
        // Verify no DB insertions occurred
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO"), eq(Long.class), any(Object[].class));
    }

}