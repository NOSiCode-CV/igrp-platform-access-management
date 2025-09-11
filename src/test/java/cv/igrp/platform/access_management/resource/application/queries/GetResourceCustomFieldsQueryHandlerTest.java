package cv.igrp.platform.access_management.resource.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.CustomFieldEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.CustomFieldEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
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
class GetResourceCustomFieldsQueryHandlerTest {

    @InjectMocks
    private GetResourceCustomFieldsQueryHandler handler;

    @Mock
    private CustomFieldEntityRepository customFieldRepository;

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Test
    void testHandle_ShouldReturnFields_WhenCustomFieldExists() {
        // Given
        Integer resourceId = 123;
        String resourceName = "resource123";
        GetResourceCustomFieldsQuery query = new GetResourceCustomFieldsQuery(resourceName);
        Map<String, Object> mockFields = new HashMap<>();
        mockFields.put("field1", "value1");
        mockFields.put("field2", 5);

        ResourceEntity resource = new ResourceEntity();
        resource.setId(resourceId);
        resource.setName(resourceName);

        CustomFieldEntity customField = new CustomFieldEntity();
        customField.setId(1);
        customField.setTableName("t_resource");
        customField.setRecordId(resourceId);
        customField.setFields(mockFields);

        when(resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED))
                .thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId))
                .thenReturn(Optional.of(customField));

        // When
        ResponseEntity<Map<String, ?>> response = handler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockFields, response.getBody());
    }

    @Test
    void testHandle_ShouldThrowException_WhenCustomFieldNotFound() {
        // Given
        Integer resourceId = 999;
        String resourceName = "resource999";
        GetResourceCustomFieldsQuery query = new GetResourceCustomFieldsQuery(resourceName);

        ResourceEntity resource = new ResourceEntity();
        resource.setId(resourceId);
        resource.setName(resourceName);

        when(resourceRepository.findByNameAndStatusNot(resourceName, Status.DELETED))
                .thenReturn(Optional.of(resource));
        when(customFieldRepository.findByTableNameAndRecordId("t_resource", resourceId))
                .thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(query));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
        assertNotNull(ex.getBody().getTitle());
        assertTrue(ex.getBody().getTitle().contains("CustomFieldEntity not found"));
    }
}
