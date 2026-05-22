package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.UserRoleAssignment;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserPermissionsQueryHandlerTest {

    @Mock private IGRPUserEntityRepository userRepository;
    @Mock private PermissionMapper permissionMapper;
    @Mock private AuthenticationHelper authenticationHelper;

    private GetCurrentUserPermissionsQueryHandler handler;
    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        handler = new GetCurrentUserPermissionsQueryHandler(userRepository, permissionMapper, authenticationHelper);
        when(authenticationHelper.getSub()).thenReturn(userId);
    }

    private PermissionEntity perm(int id, String name, Status status) {
        PermissionEntity p = new PermissionEntity();
        p.setId(id);
        p.setName(name);
        p.setStatus(status);
        return p;
    }

    private RoleEntity role(String code, PermissionEntity... perms) {
        RoleEntity r = new RoleEntity();
        r.setCode(code);
        r.setPermissions(new HashSet<>(Arrays.asList(perms)));
        return r;
    }

    private IGRPUserEntity userWithRoles(RoleEntity... roles) {
        IGRPUserEntity u = new IGRPUserEntity();
        u.setUserRoleAssignments(new ArrayList<>());
        for (RoleEntity r : roles) {
            UserRoleAssignment ura = new UserRoleAssignment();
            ura.setRole(r);
            ura.setUser(u);
            u.getUserRoleAssignments().add(ura);
        }
        return u;
    }

    @Test
    void handle_UserNotFound_Throws() {
        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.empty());
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(new GetCurrentUserPermissionsQuery()));
    }

    @Test
    void handle_ReturnsActivePermissions() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        PermissionEntity p2 = perm(2, "P2", Status.INACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1, p2));

        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        ResponseEntity<List<PermissionDTO>> resp = handler.handle(new GetCurrentUserPermissionsQuery());

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
        assertEquals(1, resp.getBody().get(0).getId());
    }

    @Test
    void handle_FiltersByRoleCode() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        PermissionEntity p2 = perm(2, "P2", Status.ACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1), role("R2", p2));

        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        GetCurrentUserPermissionsQuery q = new GetCurrentUserPermissionsQuery();
        q.setRoleCode("R2");
        ResponseEntity<List<PermissionDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
        assertEquals(2, resp.getBody().get(0).getId());
    }

    @Test
    void handle_DeduplicatesById() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1), role("R2", p1));

        when(userRepository.findByIdWithRolesAndPermissions(userId)).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        ResponseEntity<List<PermissionDTO>> resp = handler.handle(new GetCurrentUserPermissionsQuery());

        assertEquals(1, resp.getBody().size());
    }

    @Test
    void handle_InvalidTokenSub_Throws() {
        when(authenticationHelper.getSub()).thenReturn("not-a-uuid");
        assertThrows(RuntimeException.class, () -> handler.handle(new GetCurrentUserPermissionsQuery()));
    }
}
