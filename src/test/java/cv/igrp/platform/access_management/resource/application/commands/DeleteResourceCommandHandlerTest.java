package cv.igrp.platform.access_management.resource.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteResourceCommandHandler Tests")
public class DeleteResourceCommandHandlerTest {

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @InjectMocks
    private DeleteResourceCommandHandler deleteResourceCommandHandler;

    @Test
    @DisplayName("should delete resource Resource and return NO_CONTENT when resource exist")
    void testHandle_ShouldDeleteResourceAndReturnNoContent_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        String resourceName = "resource1";
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setName(resourceName);

        ResourceEntity resource = new ResourceEntity();
        resource.setId(resourceId);
        resource.setName(resourceName);

        CustomFieldEntity customField = new CustomFieldEntity();
        customField.setRecordId(resourceId);

        // Mocks
        when(resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId))
                .thenReturn(Optional.of(customField));

        doNothing().when(customFieldRepository).delete(customField);

        // When
        ResponseEntity<String> response = deleteResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(resourceRepository, times(1)).save(resource);
        verify(customFieldRepository, times(1)).delete(customField);
    }

    @Test
    @DisplayName("should throw exception when resource not found")
    void testHandle_ShouldThrowException_WhenResourceNotFound() {
        // Given
        String resourceName = "resource1";
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setName(resourceName);

        when(resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> deleteResourceCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
        assertNotNull(ex.getBody().getTitle());
        assertTrue(ex.getBody().getTitle().contains("Resource not found"));
    }

    @Test
    @DisplayName("should not delete custom field when not found")
    void testHandle_ShouldNotDeleteCustomField_WhenCustomFieldNotFound() {
        // Given
        String resourceName = "resource1";
        int resourceId = 1;
        DeleteResourceCommand command = new DeleteResourceCommand();
        command.setName(resourceName);

        ResourceEntity resource = new ResourceEntity();
        resource.setId(1);
        resource.setName(resourceName);

        // Mocks
        when(resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), resourceId))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<String> response = deleteResourceCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(resourceRepository, times(1)).save(resource);
        verify(customFieldRepository, times(0)).delete(any(CustomFieldEntity.class));
    }
}
