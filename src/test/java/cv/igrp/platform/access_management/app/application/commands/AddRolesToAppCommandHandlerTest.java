package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRolesToAppCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @InjectMocks
    private AddRolesToAppCommandHandler handler;

    private ApplicationEntity application;

    @BeforeEach
    void setUp() {
        application = new ApplicationEntity();
        application.setRoles(new HashSet<>());
    }

    @Test
    void testHandle_AddRolesSuccessfully() {

        // given
        var appCode = "APP001";
        var roleName1 = "ROLE_ADMIN";
        var roleName2 = "ROLE_USER";

        var role1 = new RoleEntity();
        role1.setName(roleName1);

        var role2 = new RoleEntity();
        role2.setName(roleName2);

        var command = mock(AddRolesToAppCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(roleName1, roleName2));

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(roleRepository.findByNameAndStatusNotDeleted(roleName1)).thenReturn(role1);
        when(roleRepository.findByNameAndStatusNotDeleted(roleName2)).thenReturn(role2);

        // when
        ResponseEntity<String> response = handler.handle(command);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(application.getRoles())
                .containsExactlyInAnyOrder(role1, role2);

        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(roleRepository).findByNameAndStatusNotDeleted(roleName1);
        verify(roleRepository).findByNameAndStatusNotDeleted(roleName2);
        verify(applicationRepository).save(application);
    }
}
