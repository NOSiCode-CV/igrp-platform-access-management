package cv.igrp.platform.access_management.app.application.queries.handlers;

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
import cv.igrp.platform.access_management.app.application.dto.*;
import cv.igrp.platform.access_management.app.application.queries.queries.*;
import cv.igrp.platform.access_management.app.application.queries.handlers.*;

import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetApplicationCustomFieldsQueryHandlerTest {

    @InjectMocks
    private GetApplicationCustomFieldsQueryHandler getApplicationCustomFieldsQueryHandler;

    @Mock
    private CustomFieldRepository customFieldRepository;

    @Test
    void testHandleGetApplicationCustomFieldsQuery_Success() {
        // Given
        Integer applicationId = 1;
        Map<String, Object> expectedFields = Map.of("key1", "value1", "key2", "value2");

        CustomField customField = new CustomField();
        customField.setTableName("t_application");
        customField.setRecordId(applicationId);
        customField.setFields(expectedFields);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.of(customField));

        GetApplicationCustomFieldsQuery query = new GetApplicationCustomFieldsQuery(applicationId);

        // When
        ResponseEntity<Map<String, ?>> response = getApplicationCustomFieldsQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedFields, response.getBody());
    }

    @Test
    void testHandleGetApplicationCustomFieldsQuery_NotFound() {
        // Given
        Integer applicationId = 1;

        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.empty());

        GetApplicationCustomFieldsQuery query = new GetApplicationCustomFieldsQuery(applicationId);

        // When & Then
        IgrpResponseStatusException thrown = assertThrows(IgrpResponseStatusException.class, () -> {
            getApplicationCustomFieldsQueryHandler.handle(query);
        });

        assertEquals(HttpStatus.NOT_FOUND, thrown.getProblem().getStatus());
        assertTrue(thrown.getProblem().getTitle().contains("CustomField not found"));
    }

}
