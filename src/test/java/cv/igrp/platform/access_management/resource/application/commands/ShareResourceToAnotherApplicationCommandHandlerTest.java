package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareResourceToAnotherApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private ResourceEntityRepository resourceEntityRepository;

    @InjectMocks
    private ShareResourceToAnotherApplicationCommandHandler handler;

    private ShareResourceToAnotherApplicationCommand command;

    @BeforeEach
    void setUp() {
        command = new ShareResourceToAnotherApplicationCommand("TestResource", "APP001");
    }

    @Test
    @DisplayName("Should share resource to another application successfully")
    void testHandle_Success() {

        // Given
        var resource = new ResourceEntity();
        resource.setId(1);
        resource.setName("TestResource");

        var app = new ApplicationEntity();
        app.setId(10);
        app.setCode("APP001");
        app.setResources(new HashSet<>());

        when(resourceEntityRepository.findByNameNotDeleted("TestResource")).thenReturn(resource);
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP001")).thenReturn(app);
        when(applicationEntityRepository.save(app)).thenReturn(app);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertAll(
                () -> assertNotNull(response),
                () -> assertEquals(200, response.getStatusCode().value()),
                () -> assertTrue(app.getResources().contains(resource))
        );

        verify(resourceEntityRepository, times(1)).findByNameNotDeleted("TestResource");
        verify(applicationEntityRepository, times(1)).findByCodeAndStatusNotDeleted("APP001");
        verify(applicationEntityRepository, times(1)).save(app);
    }
}
