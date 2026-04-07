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
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.InvitationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.utils.UserUtils;
import cv.igrp.platform.access_management.users.mapper.InvitationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Optional;
import cv.igrp.platform.access_management.shared.application.constants.IdentifierType;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteUserCommandHandlerTest {

    @Mock
    private NotificationAdapter<NotificationResult> notificationAdapter;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private InvitationEntityRepository invitationRepository;

    @Mock
    private InvitationMapper invitationMapper;

    @Mock
    private UserUtils userUtils;

    @InjectMocks
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
