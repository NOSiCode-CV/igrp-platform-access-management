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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RemoveResourceCustomFieldsCommandHandler Tests")
public class RemoveResourceCustomFieldsCommandHandlerTest {

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @InjectMocks
    private RemoveResourceCustomFieldsCommandHandler handler;

    private RemoveResourceCustomFieldsCommand removeResourceCustomFieldsCommand(
            List<String> removeResourceCustomFieldsRequest){
        return new RemoveResourceCustomFieldsCommand(removeResourceCustomFieldsRequest, "resource1");
    }

    private RemoveResourceCustomFieldsCommand command;
    private CustomFieldEntity customField;
    private ResourceEntity resource;

    @BeforeEach
    void setUp() {
        command = removeResourceCustomFieldsCommand(List.of("field1","field2"));

        Map<String, Object> existingFields = new HashMap<>();
        existingFields.put("field1", "value1");
        existingFields.put("field2", "value2");
        existingFields.put("field3", "value3");

        resource = new ResourceEntity();
        resource.setName("resource1");
        resource.setId(123);

        customField = new CustomFieldEntity();
        customField.setRecordId(123);
        customField.setTableName(CustomFieldTableName.RESOURCE.getName());
        customField.setFields(existingFields);
    }

    @Test
    @DisplayName("should remove specified fields and persist updated custom field")
    void testHandle_whenValidRequest_shouldRemoveFieldsAndSave() {
        // Arrange
        when(resourceRepository.findByNameAndStatusNot("resource1", Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123))
                .thenReturn(Optional.of(customField));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(customField.getFields().containsKey("field1"));
        assertFalse(customField.getFields().containsKey("field2"));
        assertTrue(customField.getFields().containsKey("field3"));

        verify(resourceRepository, times(1)).findByNameAndStatusNot("resource1", Status.DELETED);
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123);
        verify(customFieldRepository, times(1)).save(customField);
        verifyNoMoreInteractions(customFieldRepository);
        verifyNoMoreInteractions(resourceRepository);
    }

    @Test
    @DisplayName("should skip removal if keys list is null")
    void testHandle_whenRemoveKeysIsNull_shouldSkipRemoval() {
        // Arrange
        command = removeResourceCustomFieldsCommand(null);
        when(resourceRepository.findByNameAndStatusNot("resource1", Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123))
                .thenReturn(Optional.of(customField));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(3, customField.getFields().size());

        // Verify
        verify(resourceRepository, times(1)).findByNameAndStatusNot("resource1", Status.DELETED);
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123);
        verify(customFieldRepository, times(1)).save(customField);
    }

    @Test
    @DisplayName("should skip removal if field map is null")
    void testHandle_whenFieldsMapIsNull_shouldNotFail() {
        // Arrange
        customField.setFields(null);
        when(resourceRepository.findByNameAndStatusNot("resource1", Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123))
                .thenReturn(Optional.of(customField));

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNotNull(customField.getFields());
        assertFalse(customField.getFields().containsKey("field1"));

        // Verify
        verify(resourceRepository, times(1)).findByNameAndStatusNot("resource1", Status.DELETED);
        verify(customFieldRepository).findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123);
        verify(customFieldRepository).save(customField);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if custom field not found")
    void testHandle_whenCustomFieldNotFound_shouldThrowException() {
        // Arrange
        when(resourceRepository.findByNameAndStatusNot("resource1", Status.DELETED)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123))
                .thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertEquals("CustomFieldEntity not found", exception.getBody().getTitle());

        // Verify
        verify(resourceRepository, times(1)).findByNameAndStatusNot("resource1", Status.DELETED);
        verify(customFieldRepository).findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 123);
        verifyNoMoreInteractions(customFieldRepository);
        verifyNoMoreInteractions(resourceRepository);
    }
}
