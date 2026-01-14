package cv.igrp.platform.access_management.app.application.commands;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LinkResourceToApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationEntityRepository;

    @Mock
    private ResourceEntityRepository resourceEntityRepository;

    @InjectMocks
    private LinkResourceToApplicationCommandHandler handler;

    private ApplicationEntity application;

    @BeforeEach
    void setUp() {
        application = new ApplicationEntity();
        application.setId(1);
        application.setCode("APP1");
        application.setResources(new HashSet<>());
    }

    /**
     * Scenario: Successful linking of a single resource.
     */
    @Test
    void testHandle_singleResource_success() {

        // Given
        String appCode = "APP1";
        List<String> resources = new ArrayList<>();
        resources.add("ResourceA");

        LinkResourceToApplicationCommand command =
                new LinkResourceToApplicationCommand(resources, appCode);

        ResourceEntity resourceA = new ResourceEntity();
        resourceA.setName("ResourceA");

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode))
                .thenReturn(application);

        when(resourceEntityRepository.findByNameNotDeleted("ResourceA"))
                .thenReturn(resourceA);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(application.getResources().contains(resourceA));

        verify(applicationEntityRepository).save(application);
    }

    /**
     * Scenario: Successful linking of multiple resources.
     */
    @Test
    void testHandle_multipleResources_success() {

        // Given
        String appCode = "APP1";
        List<String> resources = new ArrayList<>();
        resources.add("ResourceA");
        resources.add("ResourceB");

        LinkResourceToApplicationCommand command =
                new LinkResourceToApplicationCommand(resources, appCode);

        ResourceEntity rA = new ResourceEntity();
        rA.setName("ResourceA");

        ResourceEntity rB = new ResourceEntity();
        rB.setName("ResourceB");

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted(appCode))
                .thenReturn(application);

        when(resourceEntityRepository.findByNameNotDeleted("ResourceA")).thenReturn(rA);
        when(resourceEntityRepository.findByNameNotDeleted("ResourceB")).thenReturn(rB);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertEquals(200, response.getStatusCode().value());
        assertTrue(application.getResources().contains(rA));
        assertTrue(application.getResources().contains(rB));

        verify(applicationEntityRepository).save(application);
    }

    /**
     * Scenario: No resources provided in the command.
     * Expected: Should still work and save the application with no modifications.
     */
    @Test
    void testHandle_noResourcesProvided_success() {

        // Given
        LinkResourceToApplicationCommand command =
                new LinkResourceToApplicationCommand(List.of(), "APP1");

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        // When
        ResponseEntity<String> response = handler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(application.getResources().isEmpty());

        verify(applicationEntityRepository).save(application);
        verify(resourceEntityRepository, never()).findByNameNotDeleted(any());
    }

    /**
     * Scenario: Application not found.
     * Expected: NullPointerException (because handler does not validate this).
     */
    @Test
    void testHandle_applicationNotFound_throwsException() {

        // Given
        LinkResourceToApplicationCommand command =
                new LinkResourceToApplicationCommand(List.of("ResourceA"), "APP_NOT_FOUND");

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP_NOT_FOUND"))
                .thenReturn(null);

        // When / Then
        assertThrows(NullPointerException.class, () -> handler.handle(command));
    }

    /**
     * Scenario: Resource not found.
     * Expected: NullPointerException (due to add(null))
     */
    @Test
    void testHandle_resourceNotFound_throwsException() {

        // Given
        LinkResourceToApplicationCommand command =
                new LinkResourceToApplicationCommand(List.of("MissingResource"), "APP1");

        when(applicationEntityRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(resourceEntityRepository.findByNameNotDeleted("MissingResource"))
                .thenThrow(IgrpResponseStatusException.class);

        // When / Then
        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));
    }
}