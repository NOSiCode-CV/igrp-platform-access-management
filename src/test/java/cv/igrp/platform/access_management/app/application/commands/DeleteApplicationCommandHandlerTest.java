package cv.igrp.platform.access_management.app.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
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
    private cv.igrp.platform.access_management.shared.domain.events.EventPublisher eventPublisher;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private MenuEntryEntityRepository menuRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Test
    void testHandle_whenApplicationFoundAndDeleted() {
        // Given
        Integer applicationId = 1;
        String applicationCode = "APP";
        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);
        application.setCode(applicationCode);
        application.setStatus(Status.ACTIVE); // Initially active

        DeleteApplicationCommand command = new DeleteApplicationCommand(applicationCode);

        // When
        when(applicationRepository.findByCodeAndStatusNot(applicationCode, Status.DELETED)).thenReturn(Optional.of(application));

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
        String applicationCode = "APP";
        DeleteApplicationCommand command = new DeleteApplicationCommand(applicationCode);

        // Then
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> deleteApplicationCommandHandler.handle(command));
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertEquals("Application not found with code: APP", exception.getBody().getTitle());
    }
}
