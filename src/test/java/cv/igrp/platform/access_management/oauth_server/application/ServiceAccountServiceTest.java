package cv.igrp.platform.access_management.oauth_server.application;

import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountDTO;
import cv.igrp.platform.access_management.oauth_server.application.dto.ServiceAccountRequestDTO;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.OAuthClientEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.entity.ServiceAccountEntity;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.OAuthClientJpaRepository;
import cv.igrp.platform.access_management.oauth_server.infrastructure.persistence.repository.ServiceAccountJpaRepository;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceAccountServiceTest {

    @Mock private ServiceAccountJpaRepository repository;
    @Mock private OAuthClientJpaRepository oauthClientRepository;
    @Mock private ApplicationEntityRepository applicationRepository;
    @Mock private RoleEntityRepository roleRepository;
    @Mock private PermissionEntityRepository permissionRepository;

    private ServiceAccountService service;

    private final UUID clientId = UUID.fromString("11111111-0000-4000-8000-000000000001");

    @BeforeEach
    void setUp() {
        service = new ServiceAccountService(repository, oauthClientRepository,
                applicationRepository, roleRepository, permissionRepository);
    }

    // ─── paired views (the reason ServiceAccountRoleDTO exists) ─────────

    @Test
    @DisplayName("roles/permissions pair each id with its own name — not by position")
    void toDto_pairsIdsWithTheirOwnNames() {
        ServiceAccountEntity entity = entityWith(
                Set.of(role(7, "AUDITOR", "FIN"), role(3, "ADMIN", "RH")),
                Set.of(permission(90, "igrp.audit.view"), permission(12, "igrp.client.list")));

        ServiceAccountDTO dto = ServiceAccountService.toDto(entity);

        // Sorted by id, and each pair internally consistent.
        assertEquals(List.of(3, 7), dto.getRoles().stream().map(r -> r.getId()).toList());
        assertEquals("ADMIN", dto.getRoles().get(0).getCode());
        assertEquals("AUDITOR", dto.getRoles().get(1).getCode());

        assertEquals(List.of(12, 90), dto.getPermissions().stream().map(p -> p.getId()).toList());
        assertEquals("igrp.client.list", dto.getPermissions().get(0).getName());
        assertEquals("igrp.audit.view", dto.getPermissions().get(1).getName());
    }

    @Test
    @DisplayName("role carries departmentCode — codes are unique only within a department")
    void toDto_rolesCarryDepartmentCode() {
        // Same code in two departments: without departmentCode these are
        // indistinguishable to a caller, which is why it is on the DTO.
        ServiceAccountEntity entity = entityWith(
                Set.of(role(1, "MANAGER", "RH"), role(2, "MANAGER", "FIN")),
                Set.of());

        ServiceAccountDTO dto = ServiceAccountService.toDto(entity);

        assertEquals(List.of("RH", "FIN"), dto.getRoles().stream().map(r -> r.getDepartmentCode()).toList());
        assertEquals(List.of("MANAGER", "MANAGER"), dto.getRoles().stream().map(r -> r.getCode()).toList());
    }

    @Test
    @DisplayName("a role with no department yields a null departmentCode, not a failure")
    void toDto_toleratesRoleWithoutDepartment() {
        ServiceAccountEntity entity = entityWith(Set.of(role(1, "GLOBAL", null)), Set.of());

        ServiceAccountDTO dto = ServiceAccountService.toDto(entity);

        assertNull(dto.getRoles().get(0).getDepartmentCode());
        assertEquals("GLOBAL", dto.getRoles().get(0).getCode());
    }

    @Test
    @DisplayName("the deprecated flat sets are still populated")
    void toDto_keepsFlatSetsForCompatibility() {
        ServiceAccountEntity entity = entityWith(
                Set.of(role(3, "ADMIN", "RH")),
                Set.of(permission(12, "igrp.client.list")));

        ServiceAccountDTO dto = ServiceAccountService.toDto(entity);

        assertEquals(Set.of(3), dto.getRoleIds());
        assertEquals(Set.of("ADMIN"), dto.getRoleCodes());
        assertEquals(Set.of(12), dto.getPermissionIds());
        assertEquals(Set.of("igrp.client.list"), dto.getPermissionNames());
    }

    @Test
    void toDto_emptyCollectionsYieldEmptyLists() {
        ServiceAccountDTO dto = ServiceAccountService.toDto(entityWith(Set.of(), Set.of()));

        assertTrue(dto.getRoles().isEmpty());
        assertTrue(dto.getPermissions().isEmpty());
    }

    // ─── 409 on the 1:1 rule ────────────────────────────────────────────

    @Test
    @DisplayName("creating a second service account for one client is a 409, not a 400")
    void create_duplicateClient_isConflict() {
        when(repository.existsByOauthClient_Id(clientId)).thenReturn(true);

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> service.create(request()));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("reassigning to a client that already has an account is a 409")
    void update_reassignToTakenClient_isConflict() {
        UUID otherClient = UUID.fromString("22222222-0000-4000-8000-000000000002");
        UUID saId = UUID.randomUUID();

        ServiceAccountEntity existing = entityWith(Set.of(), Set.of());
        existing.getOauthClient().setId(otherClient);
        when(repository.findByIdWithRolesAndPermissions(saId)).thenReturn(Optional.of(existing));
        when(repository.existsByOauthClient_Id(clientId)).thenReturn(true);

        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> service.update(saId, request()));

        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        verify(repository, never()).save(any());
    }

    // ─── unknown ids reject the whole request ───────────────────────────

    @Test
    @DisplayName("an unknown roleId rejects the request and names every missing id")
    void create_unknownRoleId_rejectsWholeRequest() {
        when(repository.existsByOauthClient_Id(clientId)).thenReturn(false);
        when(oauthClientRepository.findById(clientId)).thenReturn(Optional.of(oauthClient()));
        when(roleRepository.findAllById(any())).thenReturn(List.of(role(3, "ADMIN", "RH")));

        ServiceAccountRequestDTO req = request();
        req.setRoleIds(new LinkedHashSet<>(List.of(3, 404)));

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.create(req));

        assertTrue(ex.getMessage().contains("404"), "message should name the missing id: " + ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown permissionId rejects the request — no silent drop")
    void create_unknownPermissionId_rejectsWholeRequest() {
        when(repository.existsByOauthClient_Id(clientId)).thenReturn(false);
        when(oauthClientRepository.findById(clientId)).thenReturn(Optional.of(oauthClient()));
        when(permissionRepository.findAllById(any())).thenReturn(List.of());

        ServiceAccountRequestDTO req = request();
        req.setPermissionIds(new LinkedHashSet<>(List.of(999)));

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> service.create(req));

        assertTrue(ex.getMessage().contains("999"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("roles and permissions are NOT department-scoped — any may be granted")
    void create_acceptsRolesAndPermissionsFromAnyDepartment() {
        // Deliberate product decision: a service account may hold anything in the
        // catalog, regardless of which department or application it belongs to.
        when(repository.existsByOauthClient_Id(clientId)).thenReturn(false);
        when(oauthClientRepository.findById(clientId)).thenReturn(Optional.of(oauthClient()));
        when(roleRepository.findAllById(any()))
                .thenReturn(List.of(role(3, "ADMIN", "RH"), role(7, "AUDITOR", "FIN")));
        when(permissionRepository.findAllById(any()))
                .thenReturn(List.of(permission(12, "igrp.client.list")));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceAccountRequestDTO req = request();
        req.setRoleIds(new LinkedHashSet<>(List.of(3, 7)));
        req.setPermissionIds(new LinkedHashSet<>(List.of(12)));

        ServiceAccountDTO dto = service.create(req);

        assertEquals(List.of("RH", "FIN"),
                dto.getRoles().stream().map(r -> r.getDepartmentCode()).toList());
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private ServiceAccountRequestDTO request() {
        return ServiceAccountRequestDTO.builder()
                .name("acme-sa")
                .description("Acme service account")
                .active(true)
                .oauthClientId(clientId)
                .build();
    }

    private OAuthClientEntity oauthClient() {
        OAuthClientEntity client = new OAuthClientEntity();
        client.setId(clientId);
        client.setClientId("acme");
        return client;
    }

    private ServiceAccountEntity entityWith(Set<RoleEntity> roles, Set<PermissionEntity> permissions) {
        ServiceAccountEntity entity = new ServiceAccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("acme-sa");
        entity.setActive(true);
        entity.setOauthClient(oauthClient());
        entity.replaceRoleAssignments(new LinkedHashSet<>(roles));
        entity.replacePermissionGrants(new LinkedHashSet<>(permissions));
        return entity;
    }

    private static RoleEntity role(Integer id, String code, String departmentCode) {
        RoleEntity role = new RoleEntity();
        role.setId(id);
        role.setCode(code);
        if (departmentCode != null) {
            DepartmentEntity department = new DepartmentEntity();
            department.setCode(departmentCode);
            role.setDepartment(department);
        }
        return role;
    }

    private static PermissionEntity permission(Integer id, String name) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(id);
        permission.setName(name);
        return permission;
    }
}
