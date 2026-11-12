package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.role.domain.service.RolePermissionScopes;
import cv.igrp.platform.access_management.role.domain.service.RolePermissionsView;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RolePermissionScopeDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * v3-13 (#21): the refusals that come before anything changes. The behaviour
 * end to end is in {@code RolePermissionScopeIT}.
 */
@ExtendWith(MockitoExtension.class)
class UpdateRolePermissionScopesCommandHandlerTest {

    @Mock DepartmentEntityRepository departments;
    @Mock RoleEntityRepository roles;
    @Mock RolePermissionScopes scopes;
    @Mock RolePermissionsView view;
    @Mock EventPublisher events;
    @Mock ScopeService scopeService;
    @InjectMocks UpdateRolePermissionScopesCommandHandler handler;

    private final DepartmentEntity department = new DepartmentEntity();

    @BeforeEach
    void department() {
        department.setId(3);
        department.setCode("D1");
        when(departments.findByCodeAndStatusNotDeleted("D1")).thenReturn(department);
    }

    @Test
    void anUnknownRoleIs404() {
        when(roles.findByDepartmentAndCodeAndStatusNot(department, "R1", Status.DELETED)).thenReturn(Optional.empty());
        UpdateRolePermissionScopesCommand command = command(List.of(new RolePermissionScopeDTO("k", "own")));

        assertStatus(command, 404);
    }

    @Test
    void anEmptyRequestIsRefused() {
        role();

        assertStatus(command(List.of()), 400);
    }

    @Test
    void aMissingBodyIsRefused() {
        role();

        assertStatus(command(null), 400);
    }

    @Test
    void anEntryWithoutAKeyIsRefused() {
        role();
        List<RolePermissionScopeDTO> entries = new ArrayList<>();
        entries.add(null);

        assertStatus(command(entries), 400);
        assertStatus(command(List.of(new RolePermissionScopeDTO(" ", "own"))), 400);
    }

    @Test
    void aKeyTwiceIsRefused() {
        role();
        UpdateRolePermissionScopesCommand command = command(List.of(
                new RolePermissionScopeDTO("svc.a.list", "own"), new RolePermissionScopeDTO("svc.a.list", "all")));

        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOfSatisfying(IgrpResponseStatusException.class,
                        e -> assertThat(String.valueOf(e.getBody().getProperties())).contains("more than once"));
        verifyNoInteractions(scopes, events);
    }

    private void assertStatus(UpdateRolePermissionScopesCommand command, int status) {
        assertThatThrownBy(() -> handler.handle(command))
                .isInstanceOfSatisfying(IgrpResponseStatusException.class,
                        e -> assertThat(e.getBody().getStatus()).isEqualTo(status));
        verifyNoInteractions(scopes, events);
    }

    private void role() {
        RoleEntity role = new RoleEntity();
        role.setId(9);
        role.setCode("R1");
        when(roles.findByDepartmentAndCodeAndStatusNot(department, "R1", Status.DELETED)).thenReturn(Optional.of(role));
    }

    private static UpdateRolePermissionScopesCommand command(List<RolePermissionScopeDTO> entries) {
        return new UpdateRolePermissionScopesCommand(entries, "D1", "R1");
    }
}
