package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.shared.application.dto.RoleDepartmentDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetActiveCurrentUserRoleQueryHandlerTest {

    @Mock private IGRPUserEntityRepository userRepository;
    @Mock private AuthenticationHelper authenticationHelper;

    private GetActiveCurrentUserRoleQueryHandler handler;
    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        handler = new GetActiveCurrentUserRoleQueryHandler(userRepository, authenticationHelper);
        when(authenticationHelper.getSub()).thenReturn(userId);
    }

    @Test
    void handle_UserNotFound_ReturnsNotFound() {
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());
        ResponseEntity<RoleDepartmentDTO> resp = handler.handle(new GetActiveCurrentUserRoleQuery());
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handle_NoActiveRole_ReturnsNotFound() {
        IGRPUserEntity u = new IGRPUserEntity();
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(u));

        ResponseEntity<RoleDepartmentDTO> resp = handler.handle(new GetActiveCurrentUserRoleQuery());
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
    }

    @Test
    void handle_WithActiveRole_ReturnsDTO() {
        DepartmentEntity dept = new DepartmentEntity();
        dept.setCode("HR");
        RoleEntity role = new RoleEntity();
        role.setCode("MANAGER");
        role.setDepartment(dept);
        IGRPUserEntity u = new IGRPUserEntity();
        u.setActiveRole(role);
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(u));

        ResponseEntity<RoleDepartmentDTO> resp = handler.handle(new GetActiveCurrentUserRoleQuery());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("MANAGER", resp.getBody().roleCode());
        assertEquals("HR", resp.getBody().departmentCode());
    }
}
