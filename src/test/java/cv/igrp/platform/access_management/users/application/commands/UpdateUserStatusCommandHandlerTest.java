package cv.igrp.platform.access_management.users.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.framework.auth.core.adapter.IAdapter;
import cv.igrp.framework.auth.core.exception.IAMException;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.IGRPUserDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.users.mapper.IGRPUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class UpdateUserStatusCommandHandlerTest {

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private IGRPUserMapper userMapper;

    @Mock
    private IAdapter adapter;

    @InjectMocks
    private UpdateUserStatusCommandHandler handler;

    private IGRPUserEntity userEntity;

    @BeforeEach
    void setUp() {
        userEntity = new IGRPUserEntity();
        userEntity.setId(1);
        userEntity.setEmail("test@example.com");
        userEntity.setStatus(Status.ACTIVE);
        userEntity.setExternalId("external-1");
    }

    @Test
    void handle_deactivateUser_removesRolesAndUpdatesStatus() throws IAMException {
        // Arrange
        UpdateUserStatusCommand command = new UpdateUserStatusCommand(Status.INACTIVE.getCode(), Integer.parseInt(userEntity.getId()));

        when(userRepository.findById(Integer.parseInt(userEntity.getId()))).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any())).thenReturn(userEntity);
        when(jdbcTemplate.query(anyString(), any(PreparedStatementSetter.class), any(ResultSetExtractor.class))).thenReturn(null);
        when(adapter.getAllUserRoles()).thenReturn(Map.of("external-1", Map.of("DEPT1", Set.of("ROLE1"))));
        when(userMapper.toDto(userEntity)).thenReturn(new IGRPUserDTO());

        // Act
        ResponseEntity<?> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        verify(adapter).unassignRoleFromUser("DEPT1", "ROLE1", "external-1");
        assertEquals(Status.INACTIVE, userEntity.getStatus());
    }

    @Test
    void handle_activateUser_restoresRolesAndUpdatesStatus() throws IAMException {
        // Arrange
        userEntity.setStatus(Status.INACTIVE);
        Map<String, Set<String>> backupRoles = Map.of("DEPT1", Set.of("DEPT1.ROLE1"));
        UpdateUserStatusCommand command = new UpdateUserStatusCommand(Status.ACTIVE.getCode(), Integer.parseInt(userEntity.getId()));

        when(userRepository.findById(Integer.parseInt(userEntity.getId()))).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any())).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(new IGRPUserDTO());

        // Mock backup roles
        UpdateUserStatusCommandHandler spyHandler = spy(handler);
        doReturn(backupRoles).when(spyHandler).getUserRolesFromDatabase(Integer.parseInt(userEntity.getId()));

        // Act
        ResponseEntity<?> response = spyHandler.handle(command);

        // Assert
        assertNotNull(response);
        verify(adapter).assignRoleToUser("DEPT1", "DEPT1.ROLE1", "external-1");
        assertEquals(Status.ACTIVE, userEntity.getStatus());
    }

    @Test
    void handle_userNotFound_throwsException() {
        // Arrange
        UpdateUserStatusCommand command = new UpdateUserStatusCommand(Status.ACTIVE.getCode(), 999);

        when(userRepository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.class,
                () -> handler.handle(command));
    }

    @Test
    void handle_statusUnchanged_noRoleChange() throws IAMException {
        // Arrange
        UpdateUserStatusCommand command = new UpdateUserStatusCommand(Status.ACTIVE.getCode(), Integer.parseInt(userEntity.getId()));

        when(userRepository.findById(Integer.parseInt(userEntity.getId()))).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any())).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(new IGRPUserDTO());

        // Act
        ResponseEntity<?> response = handler.handle(command);

        // Assert
        assertNotNull(response);
        verifyNoInteractions(adapter); // No role changes should occur
    }
}