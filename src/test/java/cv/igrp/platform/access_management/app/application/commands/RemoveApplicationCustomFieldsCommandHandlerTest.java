package cv.igrp.platform.access_management.app.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class RemoveApplicationCustomFieldsCommandHandlerTest {

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @InjectMocks
    private RemoveApplicationCustomFieldsCommandHandler removeApplicationCustomFieldsCommandHandler;

    @Test
    void testHandle_shouldRemoveFieldsAndReturnNoContent() {
        // Given
        Integer appId = 1;
        String appCode = "APP";
        List<String> fieldsToRemove = List.of("custom1", "custom2");

        ApplicationEntity application = new ApplicationEntity();
        application.setId(appId);
        application.setCode(appCode);

        Map<String, Object> existingFields = new HashMap<>();
        existingFields.put("custom1", "value1");
        existingFields.put("custom2", "value2");
        existingFields.put("custom3", "value3");

        CustomFieldEntity customField = new CustomFieldEntity();
        customField.setId(10);
        customField.setTableName("t_application");
        customField.setRecordId(appId);
        customField.setFields(new HashMap<>(existingFields));

        RemoveApplicationCustomFieldsCommand command = new RemoveApplicationCustomFieldsCommand();
        command.setCode(appCode);
        command.setRemoveApplicationCustomFieldsRequest(fieldsToRemove);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", appId))
                .thenReturn(Optional.of(customField));
        when(applicationRepository.findByCodeAndStatusNot(appCode, Status.DELETED)).thenReturn(Optional.of(application));

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
        String appCode = "APP";
        RemoveApplicationCustomFieldsCommand command = new RemoveApplicationCustomFieldsCommand();
        command.setCode(appCode);
        command.setRemoveApplicationCustomFieldsRequest(List.of("field1"));

        ApplicationEntity application = new ApplicationEntity();
        application.setId(appId);
        application.setCode(appCode);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", appId))
                .thenReturn(Optional.empty());
        when(applicationRepository.findByCodeAndStatusNot(appCode, Status.DELETED)).thenReturn(Optional.of(application));

        // When + Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> removeApplicationCustomFieldsCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
        assertNotNull(ex.getBody().getTitle());
        System.out.println(ex.getBody().getTitle());
        assertTrue(ex.getBody().getTitle().contains("CustomFieldEntity not found"));

        verify(customFieldRepository, never()).save(any());
    }
}
