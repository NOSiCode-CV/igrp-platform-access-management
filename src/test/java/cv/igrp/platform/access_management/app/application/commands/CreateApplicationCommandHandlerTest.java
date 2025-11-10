package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.app.domain.service.ApplicationValidator;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.application.dto.CodeDescriptionDTO;
import cv.igrp.platform.access_management.shared.domain.validation.ResourceValidationResponse;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CreateApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    @Mock
    private ApplicationValidator applicationValidator;

    private CreateApplicationCommandHandler createApplicationCommandHandler;

    private ResourceValidationResponse resourceValidationResponse;

    @BeforeEach
    void setUp() {
        createApplicationCommandHandler = new CreateApplicationCommandHandler(applicationRepository, departmentRepository, applicationMapper, applicationValidator);
        resourceValidationResponse = new ResourceValidationResponse();
        resourceValidationResponse.setValid(true);
        resourceValidationResponse.setFailureMessage(new ArrayList<>());
    }

    @Test
    void testHandle() {
        DepartmentEntity department = new DepartmentEntity();
        department.setName("Test Department");
        department.setCode("HR");
        department.setDescription("Test Description");
        department.setStatus(DepartmentStatus.ACTIVE);
        department.setApplications(new HashSet<>());

        ApplicationDTO applicationDTO = new ApplicationDTO(
                null,
                "APP001",
                "Test Application",
                "A test app description",
                null,
                AppType.INTERNAL,
                "Admin",
                "pic.png",
                URI.create("http://localhost:8080"),
                "test-app",
                "admin",
                "2024-04-15T12:00:00",
                null,
                null,
                List.of(new CodeDescriptionDTO("HR", ""))
        );
        CreateApplicationCommand command = new CreateApplicationCommand(applicationDTO);

        // ✅ Create the expectedToSave manually
        ApplicationEntity expectedToSave = new ApplicationEntity();
        expectedToSave.setCode("APP001");
        expectedToSave.setName("Test Application");
        expectedToSave.setDescription("A test app description");
        expectedToSave.setStatus(Status.ACTIVE);
        expectedToSave.setType(AppType.INTERNAL);
        expectedToSave.setOwner("Admin");
        expectedToSave.setPicture("pic.png");
        expectedToSave.setUrl("http://localhost:8080");
        expectedToSave.setSlug("test-app");

        ApplicationEntity savedApplication = new ApplicationEntity();
        savedApplication.setId(1);
        savedApplication.setCode("APP001");
        savedApplication.setName("Test Application");
        savedApplication.setDescription("A test app description");
        savedApplication.setStatus(Status.ACTIVE);
        savedApplication.setType(AppType.INTERNAL);
        savedApplication.setOwner("Admin");
        savedApplication.setPicture("pic.png");
        savedApplication.setUrl("http://localhost:8080");
        savedApplication.setSlug("test-app");

        when(applicationValidator.validateApplicationCode(applicationDTO)).thenReturn(resourceValidationResponse);
        when(applicationMapper.toEntity(applicationDTO)).thenReturn(expectedToSave);
        when(applicationRepository.save(Mockito.any(ApplicationEntity.class))).thenReturn(savedApplication);
        when(departmentRepository.findByCodeAndStatusNot("HR", DepartmentStatus.DELETED)).thenReturn(Optional.of(department));
        when(applicationRepository.findById(1)).thenReturn(Optional.of(savedApplication));
        when(applicationMapper.toDto(savedApplication)).thenAnswer(inv -> {
            ApplicationDTO dto = new ApplicationDTO();
            dto.setId(savedApplication.getId());
            dto.setCode(savedApplication.getCode());
            dto.setName(savedApplication.getName());
            dto.setDepartments(List.of(new CodeDescriptionDTO("HR", "")));
            dto.setStatus(savedApplication.getStatus());
            return dto;
        });

        ResponseEntity<ApplicationDTO> response = createApplicationCommandHandler.handle(command);

        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getId());
        assertEquals("APP001", response.getBody().getCode());
        assertEquals("Test Application", response.getBody().getName());
        assertTrue(response.getBody().getDepartments().stream().map(CodeDescriptionDTO::getCode).toList().contains("HR"));
        assertEquals(Status.ACTIVE, response.getBody().getStatus());

        verify(applicationRepository).save(Mockito.any(ApplicationEntity.class));
    }

}
