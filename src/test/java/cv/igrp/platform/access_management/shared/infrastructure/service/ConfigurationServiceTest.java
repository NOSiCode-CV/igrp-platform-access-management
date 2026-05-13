package cv.igrp.platform.access_management.shared.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.ResultSet;
import java.sql.SQLException;

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

        // Mock DB inserts to avoid NPEs (non-user PKs remain Long)
        doReturn(1L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_department"), eq(Long.class), any(Object[].class));
        doReturn(2L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_application"), eq(Long.class), any(Object[].class));
        doReturn(3L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_permission"), eq(Long.class), any(Object[].class));
        doReturn(4L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_role"), eq(Long.class), any(Object[].class));
        doReturn(6L).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_resource"), eq(Long.class), any(Object[].class));
        // User PK is now String (UUID)
        doReturn(SUPER_ADMIN_UUID).when(jdbcTemplate).queryForObject(contains("INSERT INTO t_user"), eq(String.class), any(Object[].class));

        // Mock menus processing
        String jsonContent = "[{\"code\": \"HOME\", \"name\":\"Home\",\"url\": \"/home\",\"icon\":\"home-icon\",\"type\":\"EXTERNAL_PAGE\",\"children\":[]} ]";
        JsonNode menusNode = new ObjectMapper().readTree(jsonContent);
        doReturn(menusNode).when(objectMapper).readTree(any(InputStream.class));
        doReturn(null).when(jdbcTemplate).query(contains("SELECT fields->>'menus_hash'"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        // Execute the method under test
        configurationService.initializeSystemConfiguration();

        // Verify the user-insert was made with String.class (NOT Long.class)
        verify(jdbcTemplate).queryForObject(contains("INSERT INTO t_user"), eq(String.class), any(Object[].class));
        verify(jdbcTemplate, never()).queryForObject(contains("INSERT INTO t_user"), eq(Long.class), any(Object[].class));
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

        // SELECT id FROM t_user ... goes through getUserId (rs.getString) — return a UUID
        doAnswer(inv -> {
            ResultSetExtractor<String> extractor = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getString(1)).thenReturn(SUPER_ADMIN_UUID);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(contains("SELECT id FROM t_user"), any(ResultSetExtractor.class));

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
    @DisplayName("getUserId - reads String from result set column 1")
    @SuppressWarnings("unchecked")
    void getUserId_readsStringFromResultSet() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn(SUPER_ADMIN_UUID);

        doAnswer(inv -> {
            ResultSetExtractor<String> extractor = inv.getArgument(1);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class));

        String result = configurationService.getUserId("SELECT id FROM t_user WHERE username='x'");

        assertEquals(SUPER_ADMIN_UUID, result);
        verify(rs).getString(1);
        verify(rs, never()).getLong(anyInt());
    }

    @Test
    @DisplayName("getUserId - returns null when result set is empty")
    @SuppressWarnings("unchecked")
    void getUserId_returnsNullOnEmpty() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(false);

        doAnswer(inv -> {
            ResultSetExtractor<String> extractor = inv.getArgument(1);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class));

        assertNull(configurationService.getUserId("SELECT id FROM t_user WHERE username='nobody'"));
    }

    @Test
    @DisplayName("createSuperAdminUserInDB - calls queryForObject with String.class (not Long.class)")
    void createSuperAdminUserInDB_usesStringClass() {
        doReturn(SUPER_ADMIN_UUID).when(jdbcTemplate)
                .queryForObject(contains("INSERT INTO t_user"), eq(String.class), any(Object[].class));

        String result = configurationService.createSuperAdminUserInDB();

        assertEquals(SUPER_ADMIN_UUID, result);
        verify(jdbcTemplate).queryForObject(contains("INSERT INTO t_user"), eq(String.class), any(Object[].class));
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @Test
    @DisplayName("assignRoleToSuperAdminUserInDB - accepts String userId and binds it as a parameter")
    void assignRole_acceptsStringUserId() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);

        configurationService.assignRoleToSuperAdminUserInDB(7L, SUPER_ADMIN_UUID);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO t_user_role_assignment"), argsCaptor.capture());
        Object[] bound = argsCaptor.getValue();
        // Bound order: userId, roleId, userId, roleId
        assertEquals(SUPER_ADMIN_UUID, bound[0]);
        assertEquals(7L, bound[1]);
        assertEquals(SUPER_ADMIN_UUID, bound[2]);
        assertEquals(7L, bound[3]);
    }

    @Test
    @DisplayName("ensureSuperAdminActiveRole - accepts String userId and binds it as a parameter")
    void ensureSuperAdminActiveRole_acceptsStringUserId() {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        configurationService.ensureSuperAdminActiveRole(7L, SUPER_ADMIN_UUID);

        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("UPDATE t_user"), argsCaptor.capture());
        Object[] bound = argsCaptor.getValue();
        // Bound order: roleId, SYSTEM_USER, userId, roleId
        assertEquals(7L, bound[0]);
        assertEquals(SUPER_ADMIN_UUID, bound[2]);
        assertEquals(7L, bound[3]);
    }

    @Test
    @DisplayName("initializeSystemConfiguration - regression: when superadmin exists, the user-id is read via getUserId (String) not getId (Long)")
    @SuppressWarnings("unchecked")
    void initializeSystem_readsUserIdAsString_whenSuperAdminExists() throws Exception {
        // All entities exist
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_user"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_department"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_application"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_permission"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_resource"), eq(Integer.class));
        doReturn(1).when(jdbcTemplate).queryForObject(contains("SELECT 1 FROM t_role"), eq(Integer.class));

        // t_user query MUST use rs.getString (not getLong) — otherwise we reproduce the
        // "Bad value for type long : <uuid>" exception that motivated this fix.
        doAnswer(inv -> {
            ResultSetExtractor<String> extractor = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            // If production code calls getLong(1) on this UUID string the test fails
            // with the same SQLException the production stack trace showed.
            when(rs.getString(1)).thenReturn(SUPER_ADMIN_UUID);
            // If production code regressed to getLong(1) on this UUID, it would
            // throw "Bad value for type long" — lenient because we want the test
            // to also pass even when getLong is never invoked (the fixed path).
            org.mockito.Mockito.lenient().when(rs.getLong(1)).thenThrow(new SQLException(
                    "Bad value for type long : " + SUPER_ADMIN_UUID));
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(contains("SELECT id FROM t_user"), any(ResultSetExtractor.class));

        // The other table lookups stay on getLong path
        doAnswer(inv -> {
            ResultSetExtractor<Long> extractor = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.next()).thenReturn(true);
            when(rs.getLong(1)).thenReturn(42L);
            return extractor.extractData(rs);
        }).when(jdbcTemplate).query(argThat((String s) -> s != null && s.startsWith("SELECT id FROM ") && !s.contains("t_user")), any(ResultSetExtractor.class));

        // Mock the assignment update + count read so ensureSuperAdminActiveRole / assign chains run cleanly
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);

        // Menus json
        String jsonContent = "[]";
        JsonNode menusNode = new ObjectMapper().readTree(jsonContent);
        doReturn(menusNode).when(objectMapper).readTree(any(InputStream.class));
        doReturn(null).when(jdbcTemplate).query(contains("SELECT fields->>'menus_hash'"), any(PreparedStatementSetter.class), any(ResultSetExtractor.class));

        // Should not throw "Bad value for type long"
        assertDoesNotThrow(() -> configurationService.initializeSystemConfiguration());

        // Verify the UUID was bound to t_user_role_assignment insert as a String
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(contains("INSERT INTO t_user_role_assignment"), argsCaptor.capture());
        Object[] bound = argsCaptor.getValue();
        assertEquals(SUPER_ADMIN_UUID, bound[0], "user_id parameter must be the UUID String");
    }
}
