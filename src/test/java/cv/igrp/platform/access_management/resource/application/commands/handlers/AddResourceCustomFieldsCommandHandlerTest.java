package cv.igrp.platform.access_management.resource.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.CustomField;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.CustomFieldRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.commands.commands.*;
import cv.igrp.platform.access_management.resource.application.commands.handlers.*;
import cv.igrp.platform.access_management.resource.application.dto.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AddResourceCustomFieldsCommandHandlerTest {

    @InjectMocks
    private AddResourceCustomFieldsCommandHandler addResourceCustomFieldsCommandHandler;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @BeforeEach
    void setUp() {
        // ...
    }

    @Test
    void testHandle_ShouldAddCustomFields_WhenResourceExists() {
        // Given
        Integer resourceId = 1;
        AddResourceCustomFieldsCommand command = new AddResourceCustomFieldsCommand();
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("key1", "value1");
        command.setAddResourceCustomFieldsRequest(customFields);
        command.setId(resourceId);

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setName("Resource");

        Mockito.when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        CustomField customField = new CustomField();
        customField.setTableName("t_resource");
        customField.setRecordId(resourceId);
        customField.setFields(new HashMap<>());

        Mockito.when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId)).thenReturn(Optional.of(customField));
        Mockito.when(customFieldRepository.save(Mockito.any(CustomField.class))).thenReturn(customField);

        // When
        ResponseEntity<String> response = addResourceCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify repository calls
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId("t_resource", resourceId);
        verify(customFieldRepository, times(1)).save(Mockito.any(CustomField.class));
    }

    @Test
    void testHandle_ShouldThrowException_WhenResourceNotFound() {
        // Given
        Integer resourceId = 99;
        AddResourceCustomFieldsCommand command = new AddResourceCustomFieldsCommand();
        command.setId(resourceId);

        Mockito.when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> addResourceCustomFieldsCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("Resource not found"));
    }

    @Test
    void testHandle_ShouldCreateCustomField_WhenNoExistingCustomField() {
        // Given
        Integer resourceId = 1;
        AddResourceCustomFieldsCommand command = new AddResourceCustomFieldsCommand();
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("key2", "value2");
        command.setAddResourceCustomFieldsRequest(customFields);
        command.setId(resourceId);

        Resource resource = new Resource();
        resource.setId(resourceId);
        resource.setName("Resource");

        Mockito.when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        Mockito.when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId)).thenReturn(Optional.empty());

        CustomField newCustomField = new CustomField();
        newCustomField.setTableName("t_resource");
        newCustomField.setRecordId(resourceId);
        newCustomField.setFields(customFields);

        Mockito.when(customFieldRepository.save(Mockito.any(CustomField.class))).thenReturn(newCustomField);

        // When
        ResponseEntity<String> response = addResourceCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify repository calls
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId("t_resource", resourceId);
        verify(customFieldRepository, times(1)).save(Mockito.any(CustomField.class));
    }
}
