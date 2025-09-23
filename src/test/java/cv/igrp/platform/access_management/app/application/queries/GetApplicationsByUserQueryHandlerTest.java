package cv.igrp.platform.access_management.app.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.shared.application.dto.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetApplicationsByUserQueryHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    private GetApplicationsByUserQueryHandler getApplicationsByUserQueryHandler;

    @Mock
    private final ApplicationMapper applicationMapper = Mockito.mock(ApplicationMapper.class);

    @BeforeEach
    void setUp() {
        getApplicationsByUserQueryHandler = new GetApplicationsByUserQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationsByUserQuery() {
        // Given
        String uid = "lamar.davis";
        GetApplicationsByUserQuery query = new GetApplicationsByUserQuery(uid);

        ApplicationEntity app1 = new ApplicationEntity();
        app1.setId(1);
        app1.setName("App One");
        app1.setCode("CODE_1");

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setId(2);
        app2.setName("App Two");
        app2.setCode("CODE_2");

        ApplicationDTO app1Dto = new ApplicationDTO();
        app1Dto.setId(1);
        app1Dto.setName("App One");
        app1Dto.setCode("CODE_1");

        ApplicationDTO app2Dto = new ApplicationDTO();
        app2Dto.setId(2);
        app2Dto.setName("App Two");
        app2Dto.setCode("CODE_2");

        List<ApplicationEntity> applications = List.of(app1, app2);

        when(applicationRepository.findDistinctByDepartmentId_Roles_Users_UsernameOrDepartmentId_Roles_Users_EmailAndStatusNot(uid, uid, Status.DELETED))
                .thenReturn(applications);
        when(applicationMapper.toDto(app1)).thenReturn(app1Dto);
        when(applicationMapper.toDto(app2)).thenReturn(app2Dto);

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
