package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddRolesToAppCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

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

        var department1 = new DepartmentEntity();
        department1.setCode("DEPT001");
        department1.setApplications(Set.of(application));

        var department2 = new DepartmentEntity();
        department2.setCode("DEPT002");
        department2.setApplications(Set.of(application));

        var role1 = new RoleEntity();
        role1.setName(roleName1);
        role1.setDepartment(department1);

        var role2 = new RoleEntity();
        role2.setName(roleName2);
        role2.setDepartment(department2);

        application.setDepartments(Set.of(department1, department2));

        var command = mock(AddRolesToAppCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(roleName1, roleName2));

        when(departmentRepository.findByCodeAndStatusNotDeleted(department1.getCode())).thenReturn(department1);
        when(departmentRepository.findByCodeAndStatusNotDeleted(department2.getCode())).thenReturn(department2);
        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(any(DepartmentEntity.class), any(String.class))).thenAnswer(invocation -> {
            DepartmentEntity dept = invocation.getArgument(0);
            String code = invocation.getArgument(1);
            if (dept.getCode().equals(department1.getCode()) && code.equals(roleName1)) return role1;
            if (dept.getCode().equals(department2.getCode()) && code.equals(roleName2)) return role2;
            return null;
        });

        // when
        ResponseEntity<String> response = handler.handle(command);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(application.getRoles())
                .containsExactlyInAnyOrder(role1, role2);

        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(roleRepository).findByDepartmentAndCodeAndStatusNotDeleted(department1, roleName1);
        verify(roleRepository).findByDepartmentAndCodeAndStatusNotDeleted(department2, roleName2);
        verify(applicationRepository).save(application);
    }

    @Test
    void testHandle_ThrowsForbidden_WhenRoleDepartmentNotAssignedToApp() {
        // given
        var appCode = "APP001";
        var roleName = "ROLE_MANAGER";
        var unassignedDepartmentCode = "UNASSIGNED_DEPARTMENT";

        var unassignedDepartment = new DepartmentEntity();
        unassignedDepartment.setCode(unassignedDepartmentCode);

        var role = new RoleEntity();
        role.setName(roleName);
        role.setDepartment(unassignedDepartment); // department not in application

        var command = mock(AddRolesToAppCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(roleName));

        when(departmentRepository.findByCodeAndStatusNotDeleted(anyString())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            if (code == null) return null;
            if (code.equals(unassignedDepartment.getCode())) return unassignedDepartment;
            return null;
        });
        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(roleRepository.findByDepartmentAndCodeAndStatusNotDeleted(any(DepartmentEntity.class), any(String.class))).thenAnswer(invocation -> {
            DepartmentEntity dept = invocation.getArgument(0);
            String code = invocation.getArgument(1);
            if (dept.getCode().equals(unassignedDepartment.getCode()) && code.equals(roleName)) return role;
            return null;
        });

        // when / then
        var ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> handler.handle(command)
        );

        assertAll(
                () -> assertThat(ex.getStatusCode().value()).isEqualTo(403),
                () -> assertThat(ex.getBody().getTitle()).contains(
                        "Cannot assign role '%s' because its department '%s' is not assigned to the application '%s'".formatted(
                                roleName,
                                unassignedDepartmentCode,
                                application.getCode()
                        )
                )
        );

        // verify save is never called
        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(roleRepository).findByDepartmentAndCodeAndStatusNotDeleted(unassignedDepartment, roleName);
        verify(applicationRepository, never()).save(application);
    }
}