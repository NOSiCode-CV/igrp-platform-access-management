package cv.igrp.platform.access_management.app.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.dto.*;
import cv.igrp.platform.access_management.app.application.queries.queries.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetApplicationDeniedToUserQueryHandlerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    private final ApplicationMapper applicationMapper = new ApplicationMapper();

    private GetApplicationDeniedToUserQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GetApplicationDeniedToUserQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationDeniedToUserQuery() {
        // Given
        String uid = "lamar.davis";
        GetApplicationDeniedToUserQuery query = new GetApplicationDeniedToUserQuery(uid);

        Application app1 = new Application();
        app1.setId(1);
        app1.setCode("APP1");
        app1.setName("Application One");

        Application app2 = new Application();
        app2.setId(2);
        app2.setCode("APP2");
        app2.setName("Application Two");

        when(applicationRepository.findDeniedApplications(uid)).thenReturn(List.of(app1, app2));

        // When
        ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ApplicationDTO> result = response.getBody();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("APP1", result.get(0).getCode());
        assertEquals("Application One", result.get(0).getName());
        assertEquals("APP2", result.get(1).getCode());
        assertEquals("Application Two", result.get(1).getName());
    }
}
