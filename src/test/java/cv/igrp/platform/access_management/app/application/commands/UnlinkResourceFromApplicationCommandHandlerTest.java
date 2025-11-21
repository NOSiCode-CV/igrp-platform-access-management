package cv.igrp.platform.access_management.app.application.commands;

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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UnlinkResourceFromApplicationCommandHandler.
 * Covers:
 *  - Successful unlink of 1 and multiple resources
 *  - Ignoring non-existing resources
 *  - Application not found (handler throws NPE)
 */
@ExtendWith(MockitoExtension.class)
public class UnlinkResourceFromApplicationCommandHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @InjectMocks
    private UnlinkResourceFromApplicationCommandHandler handler;

    private ApplicationEntity application;
    private ResourceEntity resA;
    private ResourceEntity resB;
    private ResourceEntity resC;

    @BeforeEach
    void setUp() {
        // Application with 3 linked resources
        application = new ApplicationEntity();
        application.setId(10);
        application.setCode("APP1");

        resA = new ResourceEntity();
        resA.setId(1);
        resA.setName("A");

        resB = new ResourceEntity();
        resB.setId(2);
        resB.setName("B");

        resC = new ResourceEntity();
        resC.setId(3);
        resC.setName("C");

        Set<ResourceEntity> resources = new HashSet<>();
        resources.add(resA);
        resources.add(resB);
        resources.add(resC);

        application.setResources(resources);
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_unlinkSingleResource_success() {

        UnlinkResourceFromApplicationCommand cmd = new UnlinkResourceFromApplicationCommand(
                List.of("B"),
                "APP1"
        );

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(resourceRepository.findByNameNotDeleted("B"))
                .thenReturn(resB);

        when(applicationRepository.save(application)).thenReturn(application);

        ResponseEntity<String> response = handler.handle(cmd);

        assertEquals(204, response.getStatusCode().value());
        assertFalse(application.getResources().contains(resB));
        assertTrue(application.getResources().contains(resA));
        assertTrue(application.getResources().contains(resC));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_unlinkMultipleResources_success() {

        UnlinkResourceFromApplicationCommand cmd = new UnlinkResourceFromApplicationCommand(
                List.of("A", "C"),
                "APP1"
        );

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(resourceRepository.findByNameNotDeleted("A"))
                .thenReturn(resA);
        when(resourceRepository.findByNameNotDeleted("C"))
                .thenReturn(resC);

        when(applicationRepository.save(application)).thenReturn(application);

        ResponseEntity<String> response = handler.handle(cmd);

        assertEquals(204, response.getStatusCode().value());
        assertEquals(1, application.getResources().size());
        assertTrue(application.getResources().contains(resB));
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_resourceNotFound_ignore() {

        UnlinkResourceFromApplicationCommand cmd = new UnlinkResourceFromApplicationCommand(
                List.of("NON_EXISTENT"),
                "APP1"
        );

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(application);

        when(resourceRepository.findByNameNotDeleted("NON_EXISTENT"))
                .thenReturn(null);

        when(applicationRepository.save(application)).thenReturn(application);

        ResponseEntity<String> response = handler.handle(cmd);

        assertEquals(204, response.getStatusCode().value());
        assertEquals(3, application.getResources().size()); // nothing removed
    }

    // ------------------------------------------------------------------------

    @Test
    void testHandle_applicationNotFound_throwsNPE() {

        UnlinkResourceFromApplicationCommand cmd = new UnlinkResourceFromApplicationCommand(
                List.of("A"),
                "APP_NOT_FOUND"
        );

        when(applicationRepository.findByCodeAndStatusNotDeleted("APP_NOT_FOUND"))
                .thenReturn(null); // handler does not validate – will NPE

        assertThrows(NullPointerException.class, () -> handler.handle(cmd));
    }
}