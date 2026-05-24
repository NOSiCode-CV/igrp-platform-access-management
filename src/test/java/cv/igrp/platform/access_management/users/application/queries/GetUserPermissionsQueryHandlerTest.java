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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserPermissionsQueryHandlerTest {

    @Mock private IGRPUserEntityRepository userRepository;
    @Mock private PermissionMapper permissionMapper;

    private GetUserPermissionsQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetUserPermissionsQueryHandler(userRepository, permissionMapper);
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
        when(userRepository.findById("u1")).thenReturn(Optional.empty());
        GetUserPermissionsQuery q = new GetUserPermissionsQuery();
        q.setId("u1");
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(q));
    }

    @Test
    void handle_ReturnsActivePermissions() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        PermissionEntity p2 = perm(2, "P2", Status.INACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1, p2));

        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        GetUserPermissionsQuery q = new GetUserPermissionsQuery();
        q.setId("u1");
        ResponseEntity<List<PermissionDTO>> resp = handler.handle(q);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(1, resp.getBody().size());
        assertEquals(1, resp.getBody().get(0).getId());
    }

    @Test
    void handle_FiltersByRoleCode() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        PermissionEntity p2 = perm(2, "P2", Status.ACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1), role("R2", p2));

        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        GetUserPermissionsQuery q = new GetUserPermissionsQuery();
        q.setId("u1");
        q.setRoleCode("R2");
        ResponseEntity<List<PermissionDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
        assertEquals(2, resp.getBody().get(0).getId());
    }

    @Test
    void handle_DeduplicatesById() {
        PermissionEntity p1 = perm(1, "P1", Status.ACTIVE);
        IGRPUserEntity u = userWithRoles(role("R1", p1), role("R2", p1));

        when(userRepository.findById("u1")).thenReturn(Optional.of(u));
        when(permissionMapper.mapToDTO(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionDTO d = new PermissionDTO();
            d.setId(((PermissionEntity) i.getArgument(0)).getId());
            return d;
        });

        GetUserPermissionsQuery q = new GetUserPermissionsQuery();
        q.setId("u1");
        ResponseEntity<List<PermissionDTO>> resp = handler.handle(q);

        assertEquals(1, resp.getBody().size());
    }
}
