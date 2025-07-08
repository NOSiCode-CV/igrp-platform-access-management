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
public class GetApplicationsByUserQueryHandlerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    private GetApplicationsByUserQueryHandler getApplicationsByUserQueryHandler;

    private final ApplicationMapper applicationMapper = new ApplicationMapper();

    @BeforeEach
    void setUp() {
        getApplicationsByUserQueryHandler = new GetApplicationsByUserQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationsByUserQuery() {
        // Given
        String uid = "lamar.davis";
        GetApplicationsByUserQuery query = new GetApplicationsByUserQuery(uid);

        Application app1 = new Application();
        app1.setId(1);
        app1.setName("App One");
        app1.setCode("CODE_1");

        Application app2 = new Application();
        app2.setId(2);
        app2.setName("App Two");
        app2.setCode("CODE_2");

        List<Application> applications = List.of(app1, app2);

        when(applicationRepository.findDistinctByDepartments_Roles_Users_UsernameOrDepartments_Roles_Users_Email(uid, uid))
                .thenReturn(applications);

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsByUserQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("CODE_1", response.getBody().get(0).getCode());
        assertEquals("CODE_2", response.getBody().get(1).getCode());
    }
}
