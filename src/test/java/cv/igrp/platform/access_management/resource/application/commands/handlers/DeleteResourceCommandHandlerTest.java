package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.commands.handlers.*;
import cv.igrp.platform.access_management.resource.application.dto.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DeleteResourceCommandHandlerTest {

    @InjectMocks
    private DeleteResourceCommandHandler deleteResourceCommandHandler;

    @Mock
    private ResourceRepository resourceRepository;

    @BeforeEach
    void setUp() {
        // ...
    }

    @Test
    void testHandle_ShouldDeleteResourceAndReturnNoContent_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setId(resourceId);

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setName("Resource 1");


        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        doNothing().when(resourceRepository).delete(any(Resource.class));

        // When
        ResponseEntity<String> response = deleteResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(resourceRepository, times(1)).delete(resource);
    }

    @Test
    void testHandle_ShouldThrowException_WhenResourceNotFound() {
        // Given
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setId(99);

        when(resourceRepository.findById(99)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> deleteResourceCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Resource not found"));
    }
}
