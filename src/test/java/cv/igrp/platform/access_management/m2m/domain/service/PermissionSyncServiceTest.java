package cv.igrp.platform.access_management.m2m.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cv.igrp.platform.access_management.session.domain.event.RolePermissionChangedEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.events.DeletePermissionEvent;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionSyncServiceTest {

    @Mock private PermissionEntityRepository permissionRepository;
    @Mock private ResourceEntityRepository resourceEntityRepository;
    @Mock private RoleEntityRepository roleEntityRepository;
    @Mock private AuthenticationHelper authenticationHelper;
    @Mock private EventPublisher eventPublisher;
    @Mock private Jwt jwt;

    private PermissionSyncService service;

    @BeforeEach
    void setUp() {
        service = new PermissionSyncService(
                permissionRepository,
                resourceEntityRepository,
                roleEntityRepository,
                new ObjectMapper(),
                authenticationHelper,
                eventPublisher
        );
    }

    private PermissionDTO permDto(String name, String desc, Status status) {
        PermissionDTO d = new PermissionDTO();
        d.setName(name);
        d.setDescription(desc);
        d.setStatus(status);
        return d;
    }

    private ResourceEntity newResource() {
        ResourceEntity r = new ResourceEntity();
        r.setName("igrp-access-management");
        r.setPermissions(new HashSet<>());
        return r;
    }

    @Test
    void synchronize_NullList_NoOp() {
        service.synchronizePermissions(null, true);
        verifyNoInteractions(resourceEntityRepository);
        verifyNoInteractions(permissionRepository);
    }

    @Test
    void synchronize_EmptyList_NoOp() {
        service.synchronizePermissions(List.of(), true);
        verifyNoInteractions(resourceEntityRepository);
    }

    @Test
    void synchronize_AllInvalid_ThrowsValidListEmpty() {
        PermissionDTO blank = new PermissionDTO();
        blank.setName("");
        assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizePermissions(List.of(blank), true));
    }

    @Test
    void synchronize_ResourceNotFound_ThrowsNotFound() {
        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.empty());

        assertThrows(IgrpResponseStatusException.class,
                () -> service.synchronizePermissions(List.of(permDto("P1", "d", Status.ACTIVE)), true));
    }

    @Test
    void synchronize_NewPermission_Created() {
        ResourceEntity res = newResource();
        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("P_NEW", "desc", Status.ACTIVE)), true);

        verify(permissionRepository).save(argThat(p -> "P_NEW".equals(p.getName())
                && "desc".equals(p.getDescription())
                && p.getStatus() == Status.ACTIVE));
        assertEquals(1, res.getPermissions().size());
        verify(resourceEntityRepository).save(res);
    }

    @Test
    void synchronize_NewPermissionWithoutStatus_DefaultsActive() {
        ResourceEntity res = newResource();
        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("P_NEW", "d", null)), true);

        verify(permissionRepository).save(argThat(p -> p.getStatus() == Status.ACTIVE));
    }

    @Test
    void synchronize_ExistingMatches_NotUpdated() {
        ResourceEntity res = newResource();
        PermissionEntity existing = new PermissionEntity();
        existing.setName("P1");
        existing.setDescription("desc");
        existing.setStatus(Status.ACTIVE);

        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of(existing));

        service.synchronizePermissions(List.of(permDto("P1", "desc", Status.ACTIVE)), true);

        verify(permissionRepository, never()).save(any(PermissionEntity.class));
        verify(resourceEntityRepository).save(res);
    }

    @Test
    void synchronize_ExistingDiffers_Updated() {
        ResourceEntity res = newResource();
        PermissionEntity existing = new PermissionEntity();
        existing.setName("P1");
        existing.setDescription("old");
        existing.setStatus(Status.ACTIVE);

        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of(existing));

        service.synchronizePermissions(List.of(permDto("P1", "new", Status.ACTIVE)), true);

        assertEquals("new", existing.getDescription());
        verify(permissionRepository).save(existing);
    }

    @Test
    void synchronize_PermissionRemoved_SoftDeletedAndEventPublished() {
        ResourceEntity res = newResource();
        PermissionEntity gone = new PermissionEntity();
        gone.setId(42);
        gone.setName("OLD");
        gone.setDescription("d");
        gone.setStatus(Status.ACTIVE);
        gone.setDepartments(new HashSet<>());

        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of(gone));
        when(roleEntityRepository.findAllByPermissionId(42)).thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("KEEP", "d", Status.ACTIVE)), true);

        assertEquals(Status.DELETED, gone.getStatus());
        verify(eventPublisher).publishPermissionDeleted(any(DeletePermissionEvent.class));
    }

    @Test
    void synchronize_PermissionRemoved_UnlinksDepartmentsAndRoles_FiresPerRoleEvent() {
        ResourceEntity res = newResource();

        // The permission that will fall out of the sync payload.
        PermissionEntity gone = new PermissionEntity();
        gone.setId(7);
        gone.setName("OLD");
        gone.setDescription("d");
        gone.setStatus(Status.ACTIVE);

        // Two departments still hold it — both must be unlinked.
        DepartmentEntity dept1 = new DepartmentEntity();
        dept1.setCode("DEPT_HR");
        DepartmentEntity dept2 = new DepartmentEntity();
        dept2.setCode("DEPT_FIN");
        gone.setDepartments(new HashSet<>(Set.of(dept1, dept2)));

        // Two roles still hold it in their permission set — both must be scrubbed
        // and both must produce a RolePermissionChangedEvent.
        RoleEntity role1 = new RoleEntity();
        role1.setCode("MANAGER");
        role1.setDepartment(dept1);
        Set<PermissionEntity> role1Perms = new HashSet<>();
        role1Perms.add(gone);
        role1.setPermissions(role1Perms);

        RoleEntity role2 = new RoleEntity();
        role2.setCode("AUDITOR");
        role2.setDepartment(dept2);
        Set<PermissionEntity> role2Perms = new HashSet<>();
        role2Perms.add(gone);
        role2.setPermissions(role2Perms);

        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of(gone));
        when(roleEntityRepository.findAllByPermissionId(7)).thenReturn(List.of(role1, role2));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(roleEntityRepository.save(any(RoleEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("KEEP", "d", Status.ACTIVE)), true);

        // Permission itself
        assertEquals(Status.DELETED, gone.getStatus());
        assertTrue(gone.getDepartments().isEmpty(), "permission should be unlinked from departments");

        // Roles no longer hold it
        assertFalse(role1.getPermissions().stream().anyMatch(p -> Integer.valueOf(7).equals(p.getId())),
                "role1 should no longer hold the deleted permission");
        assertFalse(role2.getPermissions().stream().anyMatch(p -> Integer.valueOf(7).equals(p.getId())),
                "role2 should no longer hold the deleted permission");
        verify(roleEntityRepository).save(role1);
        verify(roleEntityRepository).save(role2);

        // One RolePermissionChangedEvent per touched role, plus one DeletePermissionEvent overall
        verify(eventPublisher, times(2))
                .publishRolePermissionChanged(any(RolePermissionChangedEvent.class));
        verify(eventPublisher, times(1))
                .publishPermissionDeleted(any(DeletePermissionEvent.class));
    }

    @Test
    void synchronize_PermissionRemoved_NoRolesHoldIt_StillPublishesDeleteEvent() {
        // Idempotency edge — the DeletePermissionEvent is the catch-all that
        // must still fire even if no role currently references the permission.
        ResourceEntity res = newResource();
        PermissionEntity gone = new PermissionEntity();
        gone.setId(99);
        gone.setName("ORPHAN");
        gone.setDescription("d");
        gone.setStatus(Status.ACTIVE);
        gone.setDepartments(new HashSet<>());

        when(resourceEntityRepository.findByNameAndStatusNot("igrp-access-management", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of(gone));
        when(roleEntityRepository.findAllByPermissionId(99)).thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("KEEP", "d", Status.ACTIVE)), true);

        assertEquals(Status.DELETED, gone.getStatus());
        verify(eventPublisher, never())
                .publishRolePermissionChanged(any(RolePermissionChangedEvent.class));
        verify(eventPublisher, times(1))
                .publishPermissionDeleted(any(DeletePermissionEvent.class));
    }

    @Test
    void synchronize_NonSystem_ResolvesResourceByClientId() {
        when(authenticationHelper.getJwtToken()).thenReturn(jwt);
        when(jwt.getClaimAsString("client_id")).thenReturn("my-client");

        ResourceEntity res = new ResourceEntity();
        res.setName("my-client");
        res.setPermissions(new HashSet<>());

        when(resourceEntityRepository.findByNameAndStatusNot("my-client", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("P1", "d", Status.ACTIVE)), false);

        verify(resourceEntityRepository).findByNameAndStatusNot("my-client", Status.DELETED);
    }

    @Test
    void synchronize_NonSystem_FallsBackToSubject() {
        when(authenticationHelper.getJwtToken()).thenReturn(jwt);
        when(jwt.getClaimAsString("client_id")).thenReturn(null);
        when(jwt.getSubject()).thenReturn("sub-client");

        ResourceEntity res = new ResourceEntity();
        res.setName("sub-client");
        res.setPermissions(new HashSet<>());

        when(resourceEntityRepository.findByNameAndStatusNot("sub-client", Status.DELETED))
                .thenReturn(Optional.of(res));
        when(permissionRepository.findAllByResourcesAndStatusNot(anySet(), eq(Status.DELETED)))
                .thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.synchronizePermissions(List.of(permDto("P1", "d", Status.ACTIVE)), false);

        verify(resourceEntityRepository).findByNameAndStatusNot("sub-client", Status.DELETED);
    }
}
