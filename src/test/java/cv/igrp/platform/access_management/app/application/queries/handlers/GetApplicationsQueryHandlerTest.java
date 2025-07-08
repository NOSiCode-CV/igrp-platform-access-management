package cv.igrp.platform.access_management.app.application.queries.handlers;


import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.dto.*;
import cv.igrp.platform.access_management.app.application.queries.queries.*;

import java.util.List;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class GetApplicationsQueryHandlerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    private GetApplicationsQueryHandler getApplicationsQueryHandler;

    private final ApplicationMapper applicationMapper = new ApplicationMapper();

    @BeforeEach
    void setUp() {
        getApplicationsQueryHandler = new GetApplicationsQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationsQuery_shouldReturnFilteredList() {
        // Given
        String code = "APP001";
        String name = "MyApp";
        String slug = "my-app-one";
        GetApplicationsQuery query = new GetApplicationsQuery(code, name, slug);

        Application app1 = new Application();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("MyApp One");
        app1.setSlug("my-app-one");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);

        Application app2 = new Application();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("MyApp Two");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.INACTIVE);

        List<Application> mockResult = List.of(app1, app2);

        // Specification should match, so mock findAll with any(Specification)
        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class))).thenReturn(mockResult);

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<ApplicationDTO> result = response.getBody();
        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("MyApp One", result.getFirst().getName());
        assertEquals("APP001", result.getFirst().getCode());
        assertEquals("my-app-one", result.getFirst().getSlug());
    }

    @Test
    void testHandleGetApplicationsQuery_shouldReturnMatchesByNameOnly() {
        // Given
        String name = "portal";
        GetApplicationsQuery query = new GetApplicationsQuery(null, name, null); // code is null

        Application app1 = new Application();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("Portal Admin");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);

        Application app2 = new Application();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("User Portal");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.INACTIVE);

        List<Application> matchingApps = List.of(app1, app2);

        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class)))
                .thenReturn(matchingApps);

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<ApplicationDTO> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());

        List<String> appNames = body.stream().map(ApplicationDTO::getName).toList();
        assertTrue(appNames.contains("Portal Admin"));
        assertTrue(appNames.contains("User Portal"));
    }

    @Test
    void testHandleGetApplicationsQuery_shouldReturnAllWhenNoFiltersProvided() {
        // Given
        GetApplicationsQuery query = new GetApplicationsQuery(null, null, null); // No filters

        Application app1 = new Application();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("Admin Console");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);

        Application app2 = new Application();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("Public Portal");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.ACTIVE);

        List<Application> allApps = List.of(app1, app2);

        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class)))
                .thenReturn(allApps);

        // When
        ResponseEntity<List<ApplicationDTO>> response = getApplicationsQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        List<ApplicationDTO> body = response.getBody();
        assertNotNull(body);
        assertEquals(2, body.size());

        assertEquals("Admin Console", body.get(0).getName());
        assertEquals("Public Portal", body.get(1).getName());
    }


}
