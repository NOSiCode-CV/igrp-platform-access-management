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
class RemoveDepartmentFromApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @InjectMocks
    private RemoveDepartmentFromApplicationCommandHandler handler;

    private ApplicationEntity application;
    private DepartmentEntity department;

    @BeforeEach
    void setUp() {
        application = new ApplicationEntity();
        application.setDepartments(new HashSet<>());

        department = new DepartmentEntity();
        department.setCode("DEPT001");
        department.setApplications(new HashSet<>());

        department.getApplications().add(application);
    }

    @Test
    void testHandle_RemoveDepartmentSuccessfully() {

        // given
        var appCode = "APP001";
        var deptCode = "DEPT001";

        var command = mock(RemoveDepartmentFromApplicationCommand.class);
        var codesDto = mock(CodeListRequestDTO.class);

        when(command.getCode()).thenReturn(appCode);
        when(command.getCodelistrequestdto()).thenReturn(codesDto);
        when(codesDto.getCodes()).thenReturn(List.of(deptCode));

        when(applicationRepository.findByCodeAndStatusNotDeleted(appCode)).thenReturn(application);
        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);

        // when
        ResponseEntity<String> response = handler.handle(command);

        // then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getStatusCode().value()).isEqualTo(204);

        assertThat(department.getApplications()).doesNotContain(application);

        verify(applicationRepository).findByCodeAndStatusNotDeleted(appCode);
        verify(departmentRepository).findByCodeAndStatusNotDeleted(deptCode);
        verify(applicationRepository).save(application);
    }
}
