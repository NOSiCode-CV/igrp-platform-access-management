package cv.igrp.platform.access_management.resource.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.CustomFieldTableName;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddResourceCustomFieldsCommandHandler Tests")
public class AddResourceCustomFieldsCommandHandlerTest {

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @InjectMocks
    private AddResourceCustomFieldsCommandHandler handler;

    private ResourceEntity resource;
    private CustomFieldEntity customField;
    private AddResourceCustomFieldsCommand command;

    private AddResourceCustomFieldsCommand addResourceCustomFieldsCommand(Map<String, ?> addResourceCustomFieldsRequest, Integer id){
        return new AddResourceCustomFieldsCommand(addResourceCustomFieldsRequest, id);
    }

    @BeforeEach
    void setUp() {
        resource = new ResourceEntity();
        resource.setId(1);

        customField = new CustomFieldEntity();
        customField.setRecordId(1);
        customField.setTableName("resource");
        customField.setFields(new HashMap<>());
    }

    @Test
    @DisplayName("should add new custom fields to existing custom field record")
    void testHandle_whenCustomFieldExists_shouldAddFields() {
        // Arrange
        Map<String, Object> fieldsToAdd = Map.of("fieldTest", "valueTest");
        command = addResourceCustomFieldsCommand(fieldsToAdd, 1);

        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 1)).thenReturn(Optional.of(customField));
        when(customFieldRepository.save(any(CustomFieldEntity.class))).thenReturn(customField);

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(customField.getFields().containsKey("fieldTest"));

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(customFieldRepository, times(1)).findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 1);
        verify(customFieldRepository, times(1)).save(customField);
    }

    @Test
    @DisplayName("should create new custom field if none exists")
    void testHandle_whenCustomFieldDoesNotExist_shouldCreateNewOne() {
        // Arrange
        Map<String, Object> fieldsToAdd = Map.of("fieldTest2", "valueTest2");
        command = addResourceCustomFieldsCommand(fieldsToAdd, 1);
        customField.setFields(fieldsToAdd);

        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId(CustomFieldTableName.RESOURCE.getName(), 1)).thenReturn(Optional.empty());
        when(customFieldRepository.save(any(CustomFieldEntity.class))).thenReturn(customField);

        // Act
        ResponseEntity<String> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(customField.getFields().containsKey("fieldTest2"));

        // Verify
        verify(customFieldRepository).save(any(CustomFieldEntity.class));
    }

    @Test
    @DisplayName("should throw exception if resource not found")
    void testHandle_whenResourceNotFound_shouldThrowException() {
        // Arrange
        Map<String, Object> fields = Map.of("keyTest3", "valueTest3");
        command = addResourceCustomFieldsCommand(fields, 999);

        when(resourceRepository.findById(999)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());

        // Verify
        verify(resourceRepository, times(1)).findById(999);
        verifyNoInteractions(customFieldRepository);
    }
}
