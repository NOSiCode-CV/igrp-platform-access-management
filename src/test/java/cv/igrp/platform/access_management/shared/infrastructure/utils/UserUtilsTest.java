package cv.igrp.platform.access_management.shared.infrastructure.utils;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserUtilsTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserUtils userUtils;

    @Test
    void handleRoleAssignmentsOnStatusChange_NoOp() {
        IGRPUserEntity user = new IGRPUserEntity();
        user.setEmail("u@x.cv");
        // Must not throw
        userUtils.handleRoleAssignmentsOnStatusChange(user, "ACTIVE", "INACTIVE", new HashMap<>());
    }

    @Test
    void constructInvitationUrl_Builds() {
        String url = userUtils.constructInvitationUrl("https://host.cv", "abc");
        assertEquals("https://host.cv/invite/accept?token=abc", url);
    }

    @Test
    void constructInvitationUrl_BlankBase_Throws() {
        assertThrows(IgrpResponseStatusException.class,
                () -> userUtils.constructInvitationUrl("", "abc"));
    }

    @Test
    void getUserRolesFromDatabase_AggregatesByDepartment() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("department_code")).thenReturn("HR", "HR", "IT");
        when(rs.getString("role_name")).thenReturn("R1", "R2", "R3");

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    mapper.mapRow(rs, 0);
                    mapper.mapRow(rs, 1);
                    mapper.mapRow(rs, 2);
                    return java.util.List.of();
                });

        Map<String, Set<String>> result = userUtils.getUserRolesFromDatabase("user-1");

        assertEquals(2, result.get("HR").size());
        assertEquals(1, result.get("IT").size());
    }

    @Test
    void getUserRolesFromDatabase_OnError_ReturnsEmpty() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        Map<String, Set<String>> result = userUtils.getUserRolesFromDatabase("user-1");

        assertTrue(result.isEmpty());
    }
}
