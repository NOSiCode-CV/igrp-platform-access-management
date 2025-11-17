package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveRoleFromApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;
    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @InjectMocks
    private RemoveRoleFromApplicationCommandHandler handler;

    private ApplicationEntity application;
    private RoleEntity role;
    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        application = new ApplicationEntity();
        application.setRoles(new HashSet<>());

        role = new RoleEntity();
        role.setName("ROLE_ADMIN");

        department = new DepartmentEntity();
        department.setCode("DEPT001");
        department.setApplications(Set.of(application));
        role.setDepartment(department);

        application.getRoles().add(role);
    }

    @Test
    void testHandle_RemoveRoleSuccessfully() {

        // given
        var appCode = "APP001";
        var roleCode = "ROLE_ADMIN";

        var command = mock(RemoveRoleFromApplicationCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(roleCode));

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(department, roleCode)).thenReturn(role);

        // when
        ResponseEntity<String> response = handler.handle(command);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(application.getRoles()).doesNotContain(role);

        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(roleRepository).findByDepartmentAndCodeAndStatusNotDeleted(department, roleCode);
        verify(applicationRepository).save(application);
    }
}
