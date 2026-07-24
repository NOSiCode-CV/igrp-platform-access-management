package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.service.ScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 behaviour test for the {@code ACCESS} settings catalog, role rows: asserts the
 * real command handlers publish the correct {@link SettingsAuditEvent}.
 * See {@link cv.igrp.platform.access_management.app.application.commands.AppSettingsAuditIT}
 * for the note on why the event→row half is covered by separate unit tests.
 */
@ExtendWith(MockitoExtension.class)
class RoleSettingsAuditIT {

    @Mock private DepartmentEntityRepository departmentRepository;
    @Mock private RoleEntityRepository roleRepository;
    @Mock private RoleMapper roleMapper;
    @Mock private EventPublisher eventPublisher;
    @Mock private ScopeService scopeService;

    @Test
    void createRolePublishesRoleCreatedEvent() {
        String departmentCode = "RH";

        RoleDTO dto = new RoleDTO();
        dto.setDepartmentCode(departmentCode);
        dto.setCode("Administrator");
        dto.setDescription("Admin role");
        dto.setParentCode(null);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);
        department.setName("Human Resources");

        RoleEntity mapped = new RoleEntity();
        mapped.setCode("Administrator");
        RoleEntity saved = new RoleEntity();
        saved.setCode("Administrator");
        saved.setName("Administrator");

        when(departmentRepository.findByCodeAndStatusNot(departmentCode, DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));
        when(roleMapper.mapToEntity(dto, department, null)).thenReturn(mapped);
        when(roleRepository.save(mapped)).thenReturn(saved);
        when(roleMapper.mapToDto(saved)).thenReturn(new RoleDTO());

        var handler = new CreateRoleCommandHandler(departmentRepository, roleRepository, roleMapper, eventPublisher, scopeService);

        handler.handle(new CreateRoleCommand(dto, departmentCode));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(RoleCreatedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.ACCESS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.ROLE);
        assertThat(event.operation()).isEqualTo(SettingsOperation.CREATE);
        assertThat(event.entityName()).isEqualTo("Administrator");
    }

    @Test
    void deleteRolePublishesRoleDeletedEvent() {
        String departmentCode = "DEPT_IGRP";
        String roleCode = "admin";

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(departmentCode);

        RoleEntity role = new RoleEntity();
        role.setId(1);
        role.setCode(roleCode);
        role.setName("Administrator");
        role.setStatus(Status.ACTIVE);
        role.setParent(new RoleEntity());
        role.setDepartment(department);

        when(departmentRepository.findByCodeAndStatusNotDeleted(departmentCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
                .thenReturn(Optional.of(role));
        when(roleRepository.save(role)).thenReturn(role);

        var handler = new DeleteRoleCommandHandler(roleRepository, departmentRepository, eventPublisher, scopeService);

        handler.handle(new DeleteRoleCommand(departmentCode, roleCode));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(RoleDeletedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.ACCESS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.ROLE);
        assertThat(event.operation()).isEqualTo(SettingsOperation.DELETE);
        assertThat(event.entityName()).isEqualTo("Administrator");
    }

    private SettingsAuditEvent capturePublished() {
        ArgumentCaptor<SettingsAuditEvent> captor = ArgumentCaptor.forClass(SettingsAuditEvent.class);
        verify(eventPublisher).publishSettingsAudit(captor.capture());
        return captor.getValue();
    }
}
