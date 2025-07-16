package cv.igrp.platform.access_management.app.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class DeleteApplicationCommandHandlerTest {

    @InjectMocks
    private DeleteApplicationCommandHandler deleteApplicationCommandHandler;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Test
    void testHandle_whenApplicationFoundAndDeleted() {
        // Given
        Integer applicationId = 1;
        ApplicationEntity application = new ApplicationEntity();
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
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> deleteApplicationCommandHandler.handle(command));
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertNotNull(exception.getBody().getProperties());
        assertEquals("Application not found with id: 999", exception.getBody().getProperties().get("details"));
    }
}
