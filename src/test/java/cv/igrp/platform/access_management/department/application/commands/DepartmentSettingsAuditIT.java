package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.domain.events.DepartmentCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.DepartmentDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 behaviour test for the {@code ACCESS} settings catalog, department rows:
 * asserts the real command handlers publish the correct {@link SettingsAuditEvent}.
 * See {@link cv.igrp.platform.access_management.app.application.commands.AppSettingsAuditIT}
 * for the note on why the event→row half is covered by separate unit tests.
 */
@ExtendWith(MockitoExtension.class)
class DepartmentSettingsAuditIT {

    @Mock private DepartmentEntityRepository departmentRepository;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private RoleEntityRepository roleRepository;
    @Mock private EventPublisher eventPublisher;

    @Test
    void createDepartmentPublishesDepartmentCreatedEvent() {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setName("Finance");
        dto.setCode("FIN");
        dto.setParentCode(null);

        DepartmentEntity mapped = new DepartmentEntity();
        DepartmentEntity saved = new DepartmentEntity();
        saved.setId(1);
        saved.setCode("FIN");
        saved.setName("Finance");

        when(departmentMapper.toEntity(dto)).thenReturn(mapped);
        when(departmentRepository.findByCodeAndStatusNot("FIN", DepartmentStatus.DELETED)).thenReturn(Optional.empty());
        when(departmentRepository.save(any(DepartmentEntity.class))).thenReturn(saved);
        when(departmentMapper.toDto(saved)).thenReturn(dto);

        var handler = new PostDepartmentCommandHandler(departmentRepository, departmentMapper, eventPublisher);

        handler.handle(new PostDepartmentCommand(dto));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(DepartmentCreatedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.ACCESS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.DEPARTMENT);
        assertThat(event.operation()).isEqualTo(SettingsOperation.CREATE);
        assertThat(event.entityName()).isEqualTo("Finance");
    }

    @Test
    void deleteDepartmentPublishesDepartmentDeletedEvent() {
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("FIN");
        department.setName("Finance");
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.existsByCode("FIN")).thenReturn(true);
        when(departmentRepository.findByCodeAndStatusNot("FIN", DepartmentStatus.DELETED))
                .thenReturn(Optional.of(department));
        when(departmentRepository.save(any(DepartmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var handler = new DeleteDepartmentCommandHandler(departmentRepository, roleRepository, eventPublisher);

        handler.handle(new DeleteDepartmentCommand("FIN"));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(DepartmentDeletedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.ACCESS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.DEPARTMENT);
        assertThat(event.operation()).isEqualTo(SettingsOperation.DELETE);
        assertThat(event.entityName()).isEqualTo("Finance");
    }

    private SettingsAuditEvent capturePublished() {
        ArgumentCaptor<SettingsAuditEvent> captor = ArgumentCaptor.forClass(SettingsAuditEvent.class);
        verify(eventPublisher).publishSettingsAudit(captor.capture());
        return captor.getValue();
    }
}
