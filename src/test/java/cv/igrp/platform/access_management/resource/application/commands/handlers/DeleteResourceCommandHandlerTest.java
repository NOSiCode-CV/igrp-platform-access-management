package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.commands.commands.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteResourceCommandHandler Tests")
public class DeleteResourceCommandHandlerTest {

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @InjectMocks
    private DeleteResourceCommandHandler deleteResourceCommandHandler;

    @Test
    @DisplayName("should delete resource Resource and return NO_CONTENT when resource exist")
    void testHandle_ShouldDeleteResourceAndReturnNoContent_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setId(resourceId);

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setName("Resource 1");

        CustomField customField = new CustomField();
        customField.setRecordId(resourceId);

        // Mocks
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId))
                .thenReturn(Optional.of(customField));

        doNothing().when(resourceRepository).delete(resource);
        doNothing().when(customFieldRepository).delete(customField);

        // When
        ResponseEntity<String> response = deleteResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(resourceRepository, times(1)).delete(resource);
        verify(customFieldRepository, times(1)).delete(customField);
    }

    @Test
    @DisplayName("should throw exception when resource not found")
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

    @Test
    @DisplayName("should not delete custom field when not found")
    void testHandle_ShouldNotDeleteCustomField_WhenCustomFieldNotFound() {
        // Given
        Integer resourceId = 1;
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setId(resourceId);

        Resource resource = new Resource();
        resource.setId(resourceId);

        // Mocks
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId))
                .thenReturn(Optional.empty());

        doNothing().when(resourceRepository).delete(resource);

        // When
        ResponseEntity<String> response = deleteResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(resourceRepository, times(1)).delete(resource);
        verify(customFieldRepository, times(0)).delete(any(CustomField.class));
    }
}
