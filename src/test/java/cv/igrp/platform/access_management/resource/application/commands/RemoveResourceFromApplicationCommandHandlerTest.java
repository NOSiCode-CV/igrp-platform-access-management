package cv.igrp.platform.access_management.resource.application.commands;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
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
class RemoveResourceFromApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private ResourceEntityRepository resourceEntityRepository;

    @InjectMocks
    private RemoveResourceFromApplicationCommandHandler handler;

    private RemoveResourceFromApplicationCommand command;
    private ApplicationEntity application;
    private ResourceEntity resource;

    @BeforeEach
    void setUp() {

        command = new RemoveResourceFromApplicationCommand("TestResource", "APP001");

        resource = new ResourceEntity();
        resource.setId(1);
        resource.setName("TestResource");

        application = new ApplicationEntity();
        application.setId(10);
        application.setCode("APP001");
        application.setResources(new HashSet<>());
        application.getResources().add(resource);
    }

    @Test
    void testHandle_RemovesResourceSuccessfully() {
        // Given
        when(resourceEntityRepository.findByNameNotDeleted("TestResource")).thenReturn(resource);
        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP001")).thenReturn(application);
        when(applicationEntityRepository.save(application)).thenReturn(application);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertAll(
                () -> assertNotNull(response),
                () -> assertEquals(204, response.getStatusCode().value(), "Expected 204 No Content"),
                () -> assertFalse(application.getResources().contains(resource), "Resource should be removed from application")
        );

        verify(resourceEntityRepository, times(1)).findByNameNotDeleted("TestResource");
        verify(applicationEntityRepository, times(1)).findByCodeAndStatusNotDeleted("APP001");
        verify(applicationEntityRepository, times(1)).save(application);
    }
}
