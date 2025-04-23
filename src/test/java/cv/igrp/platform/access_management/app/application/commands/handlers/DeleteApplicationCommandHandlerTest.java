package cv.igrp.platform.access_management.app.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.commands.commands.*;
import cv.igrp.platform.access_management.app.application.commands.handlers.*;
import cv.igrp.platform.access_management.app.application.dto.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DeleteApplicationCommandHandlerTest {

    @InjectMocks
    private DeleteApplicationCommandHandler deleteApplicationCommandHandler;

    @Mock
    private ApplicationRepository applicationRepository;

    @Test
    void testHandle_whenApplicationFoundAndDeleted() {
        // Given
        Integer applicationId = 1;
        Application application = new Application();
        application.setId(applicationId);
        application.setStatus(Status.ACTIVE); // Initially active

        DeleteApplicationCommand command = new DeleteApplicationCommand(applicationId);

        // When
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));

        ResponseEntity<String> response = deleteApplicationCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(Status.DELETED, application.getStatus());

        verify(applicationRepository, times(1)).save(application);
    }

    @Test
    void testHandle_whenApplicationNotFound() {
        // Given
        Integer applicationId = 999;  // An ID that doesn't exist
        DeleteApplicationCommand command = new DeleteApplicationCommand(applicationId);

        // When
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        // Then
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> {
            deleteApplicationCommandHandler.handle(command);
        });
        assertEquals(HttpStatus.NOT_FOUND, exception.getProblem().getStatus());
        assertEquals("Application not found with id: 999", exception.getProblem().getDetails());
    }
}
