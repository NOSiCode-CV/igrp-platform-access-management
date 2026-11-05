package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.notification.domain.service.InvitationNotificationSender;
import cv.igrp.platform.access_management.shared.application.dto.InviteUserDTO;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.platform.access_management.shared.application.constants.InvitationStatus;
import cv.igrp.platform.access_management.shared.application.dto.InvitationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.InvitationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = InviteUserCommandHandler.class)
class InviteUserCommandHandlerTest {

    @MockBean
    private InvitationNotificationSender invitationSender;

    @MockBean
    private cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository userRepository;

    @MockBean
    private RoleEntityRepository roleRepository;

    @MockBean
    private DepartmentEntityRepository departmentRepository;

    @MockBean
    private InvitationEntityRepository invitationRepository;

    @MockBean
    private InvitationMapper invitationMapper;

    @MockBean
    private UserUtils userUtils;

    @Autowired
    private InviteUserCommandHandler underTest;

    private InviteUserDTO inviteUserDTO;
    private InviteUserCommand command;

    @BeforeEach
    void setUp() {
        inviteUserDTO = new InviteUserDTO();
        inviteUserDTO.setEmail("john@nosi.cv");
        inviteUserDTO.setDepartmentCode("DEPT_TEST");
        inviteUserDTO.setRoles(List.of("ROLE_TEST"));

        command = new InviteUserCommand(inviteUserDTO);
    }

    /**
     * Test: should invite the user successfully when all conditions are valid.
     */
    @Test
    void itShouldInviteUserSuccessfully() throws NotificationException {
        when(invitationRepository.findByIdentifierTypeAndIdentifierValueAndStatus(IdentifierType.EMAIL.name(), "john@nosi.cv", InvitationStatus.PENDING)).thenReturn(Optional.empty());

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_TEST");
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_TEST")).thenReturn(department);

        RoleEntity role = new RoleEntity();
        role.setCode("ROLE_TEST");
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, "ROLE_TEST")).thenReturn(role);

        InvitationEntity savedInvitation = new InvitationEntity();
        savedInvitation.setIdentifierType(IdentifierType.EMAIL.name());
        savedInvitation.setIdentifierValue("john@nosi.cv");
        savedInvitation.setToken("test-token");
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenReturn(savedInvitation);

        when(userUtils.constructInvitationUrl(any(), eq("test-token"))).thenReturn("http://test.url");

        InvitationDTO expectedDto = new InvitationDTO();
        expectedDto.setIdentifierType(IdentifierType.EMAIL.name());
        expectedDto.setIdentifierValue("john@nosi.cv");
        when(invitationMapper.toDtoWithUrl(eq(savedInvitation), eq("http://test.url"))).thenReturn(expectedDto);

        ResponseEntity<InvitationDTO> response = underTest.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedDto, response.getBody());

        verify(invitationRepository).saveAndFlush(any(InvitationEntity.class));
        // InvitationNotificationSender.send(email, displayName, url, token, locale) is
        // the new indirection — used to be a direct notificationAdapter.send(Notification).
        verify(invitationSender).send(
                eq("john@nosi.cv"),
                eq("john@nosi.cv"),
                eq("http://test.url"),
                eq("test-token"),
                isNull());
    }

    // ─── i18n: BE-11 (FR-29, FR-30) ──────────────────────────────────────────

    private void stubHappyPath() {
        when(invitationRepository.findByIdentifierTypeAndIdentifierValueAndStatus(IdentifierType.EMAIL.name(), "john@nosi.cv", InvitationStatus.PENDING)).thenReturn(Optional.empty());
        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_TEST");
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_TEST")).thenReturn(department);
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, "ROLE_TEST")).thenReturn(new RoleEntity());
        // Echo the entity back so the persisted locale is what the handler set.
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userUtils.constructInvitationUrl(any(), any())).thenReturn("http://test.url");
    }

    @Test
    void itShouldPersistTheInviteeLocaleAndPassItToTheSender() {
        stubHappyPath();
        inviteUserDTO.setLocale("fr");

        underTest.handle(command);

        org.mockito.ArgumentCaptor<InvitationEntity> saved = org.mockito.ArgumentCaptor.forClass(InvitationEntity.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        assertEquals("fr", saved.getValue().getLocale());
        verify(invitationSender).send(eq("john@nosi.cv"), eq("john@nosi.cv"), eq("http://test.url"), any(), eq("fr"));
    }

    @Test
    void itShouldNormalizeRegionalInviteeLocale() {
        stubHappyPath();
        inviteUserDTO.setLocale("pt-CV");

        underTest.handle(command);

        verify(invitationSender).send(any(), any(), any(), any(), eq("pt"));
    }

    @Test
    void itShouldKeepANullLocaleWhenNoneIsChosen() {
        stubHappyPath();
        inviteUserDTO.setLocale("  ");

        underTest.handle(command);

        org.mockito.ArgumentCaptor<InvitationEntity> saved = org.mockito.ArgumentCaptor.forClass(InvitationEntity.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getLocale());
        verify(invitationSender).send(any(), any(), any(), any(), isNull());
    }

    @Test
    void itShouldRejectAnUnsupportedInviteeLocale() {
        when(invitationRepository.findByIdentifierTypeAndIdentifierValueAndStatus(any(), any(), any())).thenReturn(Optional.empty());
        inviteUserDTO.setLocale("de");

        var ex = assertThrows(cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException.class,
                () -> underTest.handle(command));
        assertEquals(cv.igrp.platform.access_management.shared.domain.exceptions.IgrpErrorCode.INVALID_LOCALE, ex.getCode());
        verify(invitationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(invitationSender);
    }
}
