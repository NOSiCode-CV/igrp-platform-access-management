package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
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

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class RemoveResourceCustomFieldsCommandHandlerTest {

    @InjectMocks
    private RemoveResourceCustomFieldsCommandHandler removeResourceCustomFieldsCommandHandler;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @BeforeEach
    void setUp() {
        // ...
    }

    @Test
    void testHandle_ShouldRemoveCustomFields_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        RemoveResourceCustomFieldsCommand command = new RemoveResourceCustomFieldsCommand();
        command.setId(resourceId);
        List<String> fieldsToRemove = Arrays.asList("key1", "key2");
        command.setRemoveResourceCustomFieldsRequest(fieldsToRemove);

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("key1", "value1");
        customFields.put("key2", "value2");
        CustomField customField = new CustomField();
        customField.setTableName("t_resource");
        customField.setRecordId(resourceId);
        customField.setFields(customFields);

        when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId))
                .thenReturn(Optional.of(customField));

        when(customFieldRepository.save(any(CustomField.class))).thenReturn(customField);

        // When
        ResponseEntity<String> response = removeResourceCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify repository calls
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId("t_resource", resourceId);
        verify(customFieldRepository, times(1)).save(any(CustomField.class));

        assertFalse(customField.getFields().containsKey("key1"));
        assertFalse(customField.getFields().containsKey("key2"));
    }

    @Test
    void testHandle_ShouldThrowException_WhenCustomFieldNotFound() {
        // Given
        Integer resourceId = 99;
        RemoveResourceCustomFieldsCommand command = new RemoveResourceCustomFieldsCommand();
        command.setId(resourceId);

        when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> removeResourceCustomFieldsCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("CustomField not found"));
    }

    @Test
    void testHandle_ShouldSaveUpdatedCustomField_WhenFieldsRemoved() {
        // Given
        Integer resourceId = 1;
        RemoveResourceCustomFieldsCommand command = new RemoveResourceCustomFieldsCommand();
        List<String> fieldsToRemove = List.of("key1");
        command.setRemoveResourceCustomFieldsRequest(fieldsToRemove);
        command.setId(resourceId);

        Map<String, Object> customFields = new HashMap<>();
        customFields.put("key1", "value1");
        customFields.put("key2", "value2");
        CustomField customField = new CustomField();
        customField.setTableName("t_resource");
        customField.setRecordId(resourceId);
        customField.setFields(customFields);

        when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId))
                .thenReturn(Optional.of(customField));

        when(customFieldRepository.save(any(CustomField.class))).thenReturn(customField);

        // When
        ResponseEntity<String> response = removeResourceCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(customFieldRepository, times(1)).findByTableNameAndRecordId("t_resource", resourceId);
        verify(customFieldRepository, times(1)).save(any(CustomField.class));

        assertFalse(customField.getFields().containsKey("key1"));
        assertTrue(customField.getFields().containsKey("key2"));
    }
}
