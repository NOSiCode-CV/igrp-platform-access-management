package cv.igrp.platform.access_management.app.application.queries;

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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetApplicationCustomFieldsQueryHandlerTest {

    @InjectMocks
    private GetApplicationCustomFieldsQueryHandler getApplicationCustomFieldsQueryHandler;

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Test
    void testHandleGetApplicationCustomFieldsQuery_Success() {
        // Given
        Integer applicationId = 1;
        String applicationCode = "APP";
        Map<String, Object> expectedFields = Map.of("key1", "value1", "key2", "value2");

        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);
        application.setCode(applicationCode);

        CustomFieldEntity customField = new CustomFieldEntity();
        customField.setTableName("t_application");
        customField.setRecordId(applicationId);
        customField.setFields(expectedFields);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.of(customField));
        when(applicationRepository.findByCodeAndStatusNot(applicationCode, Status.DELETED)).thenReturn(Optional.of(application));

        GetApplicationCustomFieldsQuery query = new GetApplicationCustomFieldsQuery(applicationCode);

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
        String applicationCode = "APP";

        ApplicationEntity application = new ApplicationEntity();
        application.setId(applicationId);
        application.setCode(applicationCode);

        when(customFieldRepository.findByTableNameAndRecordId("t_application", applicationId))
                .thenReturn(Optional.empty());
        when(applicationRepository.findByCodeAndStatusNot(applicationCode, Status.DELETED)).thenReturn(Optional.of(application));

        GetApplicationCustomFieldsQuery query = new GetApplicationCustomFieldsQuery(applicationCode);

        // When & Then
        IgrpResponseStatusException thrown = assertThrows(IgrpResponseStatusException.class, () -> getApplicationCustomFieldsQueryHandler.handle(query));

        assertEquals(HttpStatus.NOT_FOUND.value(), thrown.getBody().getStatus());
        assertNotNull(thrown.getBody().getTitle());
        assertTrue(thrown.getBody().getTitle().contains("CustomFieldEntity not found"));
    }

}
