package cv.igrp.platform.access_management.app.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.commands.commands.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AddApplicationCustomFieldsCommandHandlerTest {

    @InjectMocks
    private AddApplicationCustomFieldsCommandHandler addApplicationCustomFieldsCommandHandler;

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @BeforeEach
    void setUp() {
        // ...
    }

    @Test
    void testHandle_WhenApplicationExists_AndCustomFieldNotExists() {
        // Given
        Integer applicationId = 1;
        AddApplicationCustomFieldsCommand command = new AddApplicationCustomFieldsCommand();
        command.setId(applicationId);
        command.setAddApplicationCustomFieldsRequest(Map.of("field1", "value1"));

        Application application = new Application();
        application.setId(applicationId);
        application.setName("Test Application");

        CustomFieldEntity newCustomField = new CustomFieldEntity();
        newCustomField.setTableName("t_application");
        newCustomField.setRecordId(applicationId);
        newCustomField.setFields(new HashMap<>());

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.empty());

        // When
        ResponseEntity<String> response = addApplicationCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(customFieldRepository).save(any(CustomFieldEntity.class));
    }

    @Test
    void testHandle_WhenApplicationExists_AndCustomFieldExists() {
        // Given
        Integer applicationId = 1;
        AddApplicationCustomFieldsCommand command = new AddApplicationCustomFieldsCommand();
        command.setId(applicationId);
        command.setAddApplicationCustomFieldsRequest(Map.of("field1", "value1"));

        Application application = new Application();
        application.setId(applicationId);
        application.setName("Test Application");

        CustomFieldEntity existingCustomField = new CustomFieldEntity();
        existingCustomField.setTableName("t_application");
        existingCustomField.setRecordId(applicationId);
        existingCustomField.setFields(new HashMap<>(Map.of("field2", "value2")));

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.of(existingCustomField));

        // When
        ResponseEntity<String> response = addApplicationCustomFieldsCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(customFieldRepository).save(existingCustomField);
        assertTrue(existingCustomField.getFields().containsKey("field1"));
    }

    @Test
    void testHandle_WhenApplicationDoesNotExist() {
        // Given
        Integer applicationId = 1;
        AddApplicationCustomFieldsCommand command = new AddApplicationCustomFieldsCommand();
        command.setId(applicationId);
        command.setAddApplicationCustomFieldsRequest(Map.of("field1", "value1"));

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        // When & Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> addApplicationCustomFieldsCommandHandler.handle(command));

        assertEquals("Application not found", ex.getBody().getTitle());
    }
}
