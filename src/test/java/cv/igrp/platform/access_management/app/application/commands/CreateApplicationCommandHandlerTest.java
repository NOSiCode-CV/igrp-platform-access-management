package cv.igrp.platform.access_management.app.application.commands;

import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.dto.*;

import java.net.URI;

@ExtendWith(MockitoExtension.class)
public class CreateApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    private ApplicationMapper applicationMapper;

    private CreateApplicationCommandHandler createApplicationCommandHandler;

    @BeforeEach
    void setUp() {
        applicationMapper = new ApplicationMapper(); // Real mapper
        createApplicationCommandHandler = new CreateApplicationCommandHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandle() {
        // Given
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
                null
        );
        CreateApplicationCommand command = new CreateApplicationCommand(applicationDTO);

        ApplicationEntity expectedToSave = applicationMapper.toEntity(applicationDTO);
        expectedToSave.setId(null);
        expectedToSave.setStatus(Status.ACTIVE);

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

        Mockito.when(applicationRepository.save(Mockito.any(ApplicationEntity.class))).thenReturn(savedApplication);

        // When
        ResponseEntity<ApplicationDTO> response = createApplicationCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getId());
        assertEquals("APP001", response.getBody().getCode());
        assertEquals("Test Application", response.getBody().getName());
        assertEquals(Status.ACTIVE, response.getBody().getStatus());

        Mockito.verify(applicationRepository).save(Mockito.any(ApplicationEntity.class));
    }
}
