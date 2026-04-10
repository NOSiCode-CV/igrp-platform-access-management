package cv.igrp.platform.access_management.users.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.InviteUserDTO;
import cv.igrp.framework.notifications.core.adapter.NotificationAdapter;
import cv.igrp.framework.notifications.core.exception.NotificationException;
import cv.igrp.framework.notifications.core.model.Notification;
import cv.igrp.framework.notifications.core.model.NotificationResult;
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
    private NotificationAdapter<NotificationResult> notificationAdapter;

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
        inviteUserDTO.setIdentifierType(IdentifierType.EMAIL);
        inviteUserDTO.setIdentifierValue("john@nosi.cv");
        inviteUserDTO.setDepartmentCode("DEPT_TEST");
        inviteUserDTO.setRoles(List.of("ROLE_TEST"));

        command = new InviteUserCommand(inviteUserDTO);
    }

    /**
     * Test: should invite the user successfully when all conditions are valid.
     */
    @Test
    void itShouldInviteUserSuccessfully() throws NotificationException {
        when(invitationRepository.findByIdentifierTypeAndIdentifierValueAndStatus(IdentifierType.EMAIL, "john@nosi.cv", InvitationStatus.PENDING)).thenReturn(Optional.empty());

        DepartmentEntity department = new DepartmentEntity();
        department.setCode("DEPT_TEST");
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_TEST")).thenReturn(department);

        RoleEntity role = new RoleEntity();
        role.setCode("ROLE_TEST");
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, "ROLE_TEST")).thenReturn(role);

        InvitationEntity savedInvitation = new InvitationEntity();
        savedInvitation.setIdentifierType(IdentifierType.EMAIL);
        savedInvitation.setIdentifierValue("john@nosi.cv");
        savedInvitation.setToken("test-token");
        when(invitationRepository.save(any(InvitationEntity.class))).thenReturn(savedInvitation);

        when(userUtils.constructInvitationUrl(any(), eq("test-token"))).thenReturn("http://test.url");

        InvitationDTO expectedDto = new InvitationDTO();
        expectedDto.setIdentifierType(IdentifierType.EMAIL);
        expectedDto.setIdentifierValue("john@nosi.cv");
        when(invitationMapper.toDtoWithUrl(eq(savedInvitation), eq("http://test.url"))).thenReturn(expectedDto);

        ResponseEntity<InvitationDTO> response = underTest.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedDto, response.getBody());

        verify(invitationRepository).save(any(InvitationEntity.class));
        verify(notificationAdapter).send(any(Notification.class));
    }
}
