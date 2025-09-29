package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.application.dto.CodeListRequestDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
class AddDepartmentsToApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @InjectMocks
    private AddDepartmentsToApplicationCommandHandler handler;

    private ApplicationEntity application;

    @BeforeEach
    void setUp() {
        application = new ApplicationEntity();
        application.setDepartments(new HashSet<>());
    }

    @Test
    void testHandle_AddDepartmentsSuccessfully() {

        // given
        var appCode = "APP001";
        var deptCode1 = "DEPT001";
        var deptCode2 = "DEPT002";

        var department1 = new DepartmentEntity();
        department1.setCode(deptCode1);

        var department2 = new DepartmentEntity();
        department2.setCode(deptCode2);

        var command = mock(AddDepartmentsToApplicationCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(deptCode1, deptCode2));

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode1)).thenReturn(department1);
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode2)).thenReturn(department2);

        // when
        ResponseEntity<String> response = handler.handle(command);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(application.getDepartments()).containsExactlyInAnyOrder(department1, department2);

        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(departmentRepository).findByCodeAndStatusNotDeleted(deptCode1);
        verify(departmentRepository).findByCodeAndStatusNotDeleted(deptCode2);
        verify(applicationRepository).save(application);
    }
}
