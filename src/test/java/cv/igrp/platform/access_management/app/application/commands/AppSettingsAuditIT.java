package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.app.domain.service.ApplicationValidator;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.events.EventPublisher;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 3 behaviour test for the {@code APPLICATIONS} settings catalog: exercises the
 * real command handlers and asserts they publish the correct {@link SettingsAuditEvent}
 * — the payload the {@code SettingsAuditEventListener} turns into one
 * {@code t_security_audit_log} row (see validation.md §Phase 3).
 *
 * <p>The event→row half (typed columns, IP/user-agent capture, hash-chain integrity) is
 * covered by {@code SettingsAuditEventListenerTest} and {@code SecurityAuditServiceImplTest}.
 * A full Spring + Postgres round-trip is deferred until the suite gains Testcontainers
 * (the project currently has no integration DB harness).
 */
@ExtendWith(MockitoExtension.class)
class AppSettingsAuditIT {

    @Mock private ApplicationEntityRepository applicationRepository;
    @Mock private DepartmentEntityRepository departmentRepository;
    @Mock private MenuEntryEntityRepository menuRepository;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private ApplicationValidator applicationValidator;
    @Mock private EventPublisher eventPublisher;

    @Test
    void createApplicationPublishesApplicationCreatedEvent() {
        ApplicationDTO dto = new ApplicationDTO();
        dto.setCode("APP001");
        dto.setName("Payments");
        dto.setDepartments(null);

        ApplicationEntity mapped = new ApplicationEntity();
        ApplicationEntity saved = new ApplicationEntity();
        saved.setId(1);
        saved.setCode("APP001");
        saved.setName("Payments");

        ResourceValidationResponse valid = new ResourceValidationResponse();
        valid.setValid(true);
        valid.setFailureMessage(new ArrayList<>());

        when(applicationValidator.validateApplicationCode(dto)).thenReturn(valid);
        when(applicationMapper.toEntity(dto)).thenReturn(mapped);
        when(applicationRepository.save(any(ApplicationEntity.class))).thenReturn(saved);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(saved));
        when(applicationMapper.toDto(saved)).thenReturn(dto);

        var handler = new CreateApplicationCommandHandler(
                applicationRepository, departmentRepository, applicationMapper, applicationValidator, eventPublisher);

        handler.handle(new CreateApplicationCommand(dto));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(ApplicationCreatedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.APPLICATIONS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.APPLICATION);
        assertThat(event.operation()).isEqualTo(SettingsOperation.CREATE);
        assertThat(event.entityName()).isEqualTo("Payments");
    }

    @Test
    void deleteApplicationPublishesApplicationDeletedEvent() {
        ApplicationEntity application = new ApplicationEntity();
        application.setId(1);
        application.setCode("APP001");
        application.setName("Payments");
        application.setStatus(Status.ACTIVE);

        when(applicationRepository.findByCodeAndStatusNot("APP001", Status.DELETED))
                .thenReturn(Optional.of(application));

        var handler = new DeleteApplicationCommandHandler(applicationRepository, menuRepository, departmentRepository, eventPublisher);

        handler.handle(new DeleteApplicationCommand("APP001"));

        SettingsAuditEvent event = capturePublished();
        assertThat(event).isInstanceOf(ApplicationDeletedEvent.class);
        assertThat(event.area()).isEqualTo(SettingsArea.APPLICATIONS);
        assertThat(event.entityType()).isEqualTo(SettingsEntityType.APPLICATION);
        assertThat(event.operation()).isEqualTo(SettingsOperation.DELETE);
        assertThat(event.entityName()).isEqualTo("Payments");
    }

    private SettingsAuditEvent capturePublished() {
        ArgumentCaptor<SettingsAuditEvent> captor = ArgumentCaptor.forClass(SettingsAuditEvent.class);
        verify(eventPublisher).publishSettingsAudit(captor.capture());
        return captor.getValue();
    }
}
