package cv.igrp.platform.access_management.security_audit.application.config;

import cv.igrp.platform.access_management.security_audit.application.service.SecurityAuditService;
import cv.igrp.platform.access_management.security_audit.domain.enums.AuditStatus;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsArea;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsEntityType;
import cv.igrp.platform.access_management.security_audit.domain.enums.SettingsOperation;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationActivatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationAssociatedToRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDeactivatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationDisassociatedFromRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.ApplicationEditedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.DepartmentCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.DepartmentDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.DepartmentEditedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.MenuAssociatedToRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.MenuDisassociatedFromRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionAssociatedToRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.PermissionDisassociatedFromRoleEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleAssignedToUserEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleCreatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleDeletedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleEditedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.RoleUnassignedFromUserEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.SettingsAuditEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.UserActivatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.UserDeactivatedEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.UserInviteCancelledEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.UserInviteResentEvent;
import cv.igrp.platform.access_management.security_audit.domain.events.UserInvitedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link SettingsAuditEventListener} unpacks every catalog event into
 * the correct {@code SecurityAuditService.logSettingsEvent(...)} call — one case per
 * event class (Phase 3, validation.md §Phase 3 "Compile & unit").
 */
@ExtendWith(MockitoExtension.class)
class SettingsAuditEventListenerTest {

    @Mock
    private SecurityAuditService securityAuditService;

    /** Each argument row is one catalog event plus its expected typed columns. */
    static Stream<Arguments> events() {
        return Stream.of(
                // --- APPLICATIONS ---
                Arguments.of(new ApplicationCreatedEvent("Payments"),
                        SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.CREATE,
                        "Payments", null, null, null),
                Arguments.of(new ApplicationEditedEvent("Payments", "name=Old", "name=Payments"),
                        SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.EDIT,
                        "Payments", null, "name=Old", "name=Payments"),
                Arguments.of(new ApplicationDeletedEvent("Payments"),
                        SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.DELETE,
                        "Payments", null, null, null),
                Arguments.of(new ApplicationActivatedEvent("Payments"),
                        SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.ACTIVATE,
                        "Payments", null, null, null),
                Arguments.of(new ApplicationDeactivatedEvent("Payments"),
                        SettingsArea.APPLICATIONS, SettingsEntityType.APPLICATION, SettingsOperation.DEACTIVATE,
                        "Payments", null, null, null),
                // --- USERS ---
                Arguments.of(new UserInvitedEvent("alice@x.cv"),
                        SettingsArea.USERS, SettingsEntityType.USER, SettingsOperation.INVITE,
                        "alice@x.cv", null, null, null),
                Arguments.of(new UserInviteCancelledEvent("alice@x.cv"),
                        SettingsArea.USERS, SettingsEntityType.USER, SettingsOperation.CANCEL_INVITE,
                        "alice@x.cv", null, null, null),
                Arguments.of(new UserInviteResentEvent("alice@x.cv"),
                        SettingsArea.USERS, SettingsEntityType.USER, SettingsOperation.RESEND_INVITE,
                        "alice@x.cv", null, null, null),
                Arguments.of(new UserActivatedEvent("alice@x.cv"),
                        SettingsArea.USERS, SettingsEntityType.USER, SettingsOperation.ACTIVATE,
                        "alice@x.cv", null, null, null),
                Arguments.of(new UserDeactivatedEvent("alice@x.cv"),
                        SettingsArea.USERS, SettingsEntityType.USER, SettingsOperation.DEACTIVATE,
                        "alice@x.cv", null, null, null),
                // --- ACCESS: department ---
                Arguments.of(new DepartmentCreatedEvent("Finance"),
                        SettingsArea.ACCESS, SettingsEntityType.DEPARTMENT, SettingsOperation.CREATE,
                        "Finance", null, null, null),
                Arguments.of(new DepartmentEditedEvent("Finance", "name=Old", "name=Finance"),
                        SettingsArea.ACCESS, SettingsEntityType.DEPARTMENT, SettingsOperation.EDIT,
                        "Finance", null, "name=Old", "name=Finance"),
                Arguments.of(new DepartmentDeletedEvent("Finance"),
                        SettingsArea.ACCESS, SettingsEntityType.DEPARTMENT, SettingsOperation.DELETE,
                        "Finance", null, null, null),
                // --- ACCESS: role ---
                Arguments.of(new RoleCreatedEvent("Administrator"),
                        SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.CREATE,
                        "Administrator", null, null, null),
                Arguments.of(new RoleEditedEvent("Administrator", "name=Old", "name=Administrator"),
                        SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.EDIT,
                        "Administrator", null, "name=Old", "name=Administrator"),
                Arguments.of(new RoleDeletedEvent("Administrator"),
                        SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.DELETE,
                        "Administrator", null, null, null),
                // --- ACCESS: permission ↔ role ---
                Arguments.of(new PermissionAssociatedToRoleEvent("APPROVE", "DEPT.Admin"),
                        SettingsArea.ACCESS, SettingsEntityType.PERMISSION, SettingsOperation.ASSOCIATE,
                        "APPROVE", "DEPT.Admin", null, null),
                Arguments.of(new PermissionDisassociatedFromRoleEvent("APPROVE", "DEPT.Admin"),
                        SettingsArea.ACCESS, SettingsEntityType.PERMISSION, SettingsOperation.DISASSOCIATE,
                        "APPROVE", "DEPT.Admin", null, null),
                // --- ACCESS: application ↔ role ---
                Arguments.of(new ApplicationAssociatedToRoleEvent("Payments", "FIN"),
                        SettingsArea.ACCESS, SettingsEntityType.APPLICATION, SettingsOperation.ASSOCIATE,
                        "Payments", "FIN", null, null),
                Arguments.of(new ApplicationDisassociatedFromRoleEvent("Payments", "FIN"),
                        SettingsArea.ACCESS, SettingsEntityType.APPLICATION, SettingsOperation.DISASSOCIATE,
                        "Payments", "FIN", null, null),
                // --- ACCESS: menu ↔ role ---
                Arguments.of(new MenuAssociatedToRoleEvent("MENU_HOME", "FIN"),
                        SettingsArea.ACCESS, SettingsEntityType.MENU, SettingsOperation.ASSOCIATE,
                        "MENU_HOME", "FIN", null, null),
                Arguments.of(new MenuDisassociatedFromRoleEvent("MENU_HOME", "FIN"),
                        SettingsArea.ACCESS, SettingsEntityType.MENU, SettingsOperation.DISASSOCIATE,
                        "MENU_HOME", "FIN", null, null),
                // --- ACCESS: role ↔ user ---
                Arguments.of(new RoleAssignedToUserEvent("DEPT.Admin", "user-1"),
                        SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.ASSIGN,
                        "DEPT.Admin", "user-1", null, null),
                Arguments.of(new RoleUnassignedFromUserEvent("DEPT.Admin", "user-1"),
                        SettingsArea.ACCESS, SettingsEntityType.ROLE, SettingsOperation.UNASSIGN,
                        "DEPT.Admin", "user-1", null, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("events")
    void delegatesEachEventToLogSettingsEvent(SettingsAuditEvent event,
                                              SettingsArea area,
                                              SettingsEntityType entityType,
                                              SettingsOperation operation,
                                              String entityName,
                                              String relatedEntity,
                                              String previousValue,
                                              String newValue) {
        // Sanity-check the event's own typed accessors match the catalog expectation.
        assertThat(event.area()).isEqualTo(area);
        assertThat(event.entityType()).isEqualTo(entityType);
        assertThat(event.operation()).isEqualTo(operation);
        assertThat(event.status()).isEqualTo(AuditStatus.SUCCESS);

        new SettingsAuditEventListener(securityAuditService).onSettingsAuditEvent(event);

        verify(securityAuditService).logSettingsEvent(
                area, entityType, operation, entityName, relatedEntity, previousValue, newValue,
                AuditStatus.SUCCESS);
    }

    @Test
    void listenerNeverPropagatesAuditFailures() {
        doThrow(new RuntimeException("db down"))
                .when(securityAuditService)
                .logSettingsEvent(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> new SettingsAuditEventListener(securityAuditService)
                .onSettingsAuditEvent(new RoleCreatedEvent("Administrator")))
                .doesNotThrowAnyException();
    }
}
