package cv.igrp.platform.access_management.app.application.commands.handlers;

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
import cv.igrp.platform.access_management.app.application.commands.commands.*;
import cv.igrp.platform.access_management.app.application.commands.handlers.*;
import cv.igrp.platform.access_management.app.application.dto.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class RemoveApplicationCustomFieldsCommandHandlerTest {

    @Mock
    private CustomFieldRepository customFieldRepository;

    @InjectMocks
    private RemoveApplicationCustomFieldsCommandHandler removeApplicationCustomFieldsCommandHandler;

    @Test
    void testHandle_shouldRemoveFieldsAndReturnNoContent() {
        // Given
        Integer appId = 1;
        List<String> fieldsToRemove = List.of("custom1", "custom2");

        Map<String, Object> existingFields = new HashMap<>();
        existingFields.put("custom1", "value1");
        existingFields.put("custom2", "value2");
        existingFields.put("custom3", "value3");

        CustomField customField = new CustomField();
        customField.setId(10);
        customField.setTableName("t_application");
        customField.setRecordId(appId);
        customField.setFields(new HashMap<>(existingFields));

        RemoveApplicationCustomFieldsCommand command = new RemoveApplicationCustomFieldsCommand();
        command.setId(appId);
        command.setRemoveApplicationCustomFieldsRequest(fieldsToRemove);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", appId))
                .thenReturn(Optional.of(customField));

        // When
        ResponseEntity<String> response = removeApplicationCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        Map<String, Object> updatedFields = customField.getFields();
        assertFalse(updatedFields.containsKey("custom1"));
        assertFalse(updatedFields.containsKey("custom2"));
        assertTrue(updatedFields.containsKey("custom3"));

        verify(customFieldRepository).save(customField);
    }

    @Test
    void testHandle_shouldThrowNotFoundIfCustomFieldDoesNotExist() {
        // Given
        Integer appId = 99;
        RemoveApplicationCustomFieldsCommand command = new RemoveApplicationCustomFieldsCommand();
        command.setId(appId);
        command.setRemoveApplicationCustomFieldsRequest(List.of("field1"));

        when(customFieldRepository.findByTableNameAndRecordId("t_application", appId))
                .thenReturn(Optional.empty());

        // When + Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> removeApplicationCustomFieldsCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND, ex.getProblem().getStatus());
        assertTrue(ex.getProblem().getTitle().contains("CustomField not found"));

        verify(customFieldRepository, never()).save(any());
    }
}
