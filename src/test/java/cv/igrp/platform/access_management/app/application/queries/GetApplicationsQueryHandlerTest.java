package cv.igrp.platform.access_management.app.application.queries;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
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

import java.net.URI;
import java.util.List;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class GetApplicationsQueryHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    private GetApplicationsQueryHandler getApplicationsQueryHandler;

    @Mock
    private final ApplicationMapper applicationMapper = Mockito.mock(ApplicationMapper.class);

    @BeforeEach
    void setUp() {
        getApplicationsQueryHandler = new GetApplicationsQueryHandler(applicationRepository, applicationMapper);
    }

    DepartmentEntity department;

    @Test
    void testHandleGetApplicationsQuery_shouldReturnFilteredList() {

        department = new DepartmentEntity();
        department.setName("Test Department");
        department.setDescription("Test Description");
        department.setCode("HR");

        // Given
        String code = "APP001";
        String name = "MyApp";
        String slug = "my-app-one";
        String department = "HR";
        GetApplicationsQuery query = new GetApplicationsQuery(code, name, slug, department);

        ApplicationEntity app1 = new ApplicationEntity();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("MyApp One");
        app1.setSlug("my-app-one");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("MyApp Two");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.INACTIVE);

        ApplicationDTO app1Dto = new ApplicationDTO();
        app1Dto.setId(1);
        app1Dto.setCode("APP001");
        app1Dto.setName("MyApp One");
        app1Dto.setSlug("my-app-one");
        app1Dto.setType(AppType.INTERNAL);
        app1Dto.setStatus(Status.ACTIVE);

        ApplicationDTO app2Dto = new ApplicationDTO();
        app2Dto.setId(2);
        app2Dto.setCode("APP002");
        app2Dto.setName("MyApp Two");
        app2Dto.setType(AppType.EXTERNAL);
        app2Dto.setStatus(Status.INACTIVE);

        List<ApplicationEntity> mockResult = List.of(app1, app2);

        // Specification should match, so mock findAll with any(Specification)
        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class))).thenReturn(mockResult);
        when(applicationMapper.toDto(app1)).thenReturn(app1Dto);
        when(applicationMapper.toDto(app2)).thenReturn(app2Dto);

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
        GetApplicationsQuery query = new GetApplicationsQuery(null, name, null, "HR"); // code is null

        ApplicationEntity app1 = new ApplicationEntity();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("Portal Admin");
        app1.setSlug("my-app-one");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);
        app1.setDepartmentId(department);

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("User Portal");
        app2.setSlug("my-app-two");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.INACTIVE);
        app2.setDepartmentId(department);

        ApplicationDTO app1Dto = new ApplicationDTO();
        app1Dto.setId(1);
        app1Dto.setCode("APP001");
        app1Dto.setName("Portal Admin");
        app1Dto.setSlug("my-app-one");
        app1Dto.setType(AppType.INTERNAL);
        app1Dto.setStatus(Status.ACTIVE);

        ApplicationDTO app2Dto = new ApplicationDTO();
        app2Dto.setId(2);
        app2Dto.setCode("APP002");
        app2Dto.setName("User Portal");
        app1Dto.setSlug("my-app-two");
        app2Dto.setType(AppType.EXTERNAL);
        app2Dto.setStatus(Status.INACTIVE);

        List<ApplicationEntity> matchingApps = List.of(app1, app2);

        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class)))
                .thenReturn(matchingApps);
        when(applicationMapper.toDto(app1)).thenReturn(app1Dto);
        when(applicationMapper.toDto(app2)).thenReturn(app2Dto);

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
        GetApplicationsQuery query = new GetApplicationsQuery(null, null, null, "HR"); // No filters

        ApplicationEntity app1 = new ApplicationEntity();
        app1.setId(1);
        app1.setCode("APP001");
        app1.setName("Admin Console");
        app1.setSlug("my-app-one");
        app1.setType(AppType.INTERNAL);
        app1.setStatus(Status.ACTIVE);
        app1.setDepartmentId(department);

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setId(2);
        app2.setCode("APP002");
        app2.setName("Public Portal");
        app2.setUrl("https://my-app-two.com");
        app2.setType(AppType.EXTERNAL);
        app2.setStatus(Status.ACTIVE);
        app2.setDepartmentId(department);

        ApplicationDTO app1Dto = new ApplicationDTO();

        app1Dto.setId(1);
        app1Dto.setCode("APP001");
        app1Dto.setName("Admin Console");
        app1Dto.setSlug("my-app-one");
        app1Dto.setType(AppType.INTERNAL);
        app1Dto.setStatus(Status.ACTIVE);

        ApplicationDTO app2Dto = new ApplicationDTO();
        app2Dto.setId(2);
        app2Dto.setCode("APP002");
        app2Dto.setName("Public Portal");
        app2Dto.setUrl(URI.create("https://my-app-two.com"));
        app2Dto.setType(AppType.EXTERNAL);
        app2Dto.setStatus(Status.INACTIVE);

        List<ApplicationEntity> allApps = List.of(app1, app2);

        Mockito.when(applicationRepository.findAll(Mockito.any(Specification.class)))
                .thenReturn(allApps);
        when(applicationMapper.toDto(app1)).thenReturn(app1Dto);
        when(applicationMapper.toDto(app2)).thenReturn(app2Dto);

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
