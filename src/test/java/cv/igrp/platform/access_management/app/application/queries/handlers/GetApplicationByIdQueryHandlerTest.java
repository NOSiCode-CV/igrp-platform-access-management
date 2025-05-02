package cv.igrp.platform.access_management.app.application.queries.handlers;


import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.app.application.dto.*;
import cv.igrp.platform.access_management.app.application.queries.queries.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetApplicationByIdQueryHandlerTest {

    @Mock
    private ApplicationRepository applicationRepository;

    private GetApplicationByIdQueryHandler getApplicationByIdQueryHandler;

    private ApplicationMapper applicationMapper = new ApplicationMapper();

    @BeforeEach
    void setUp() {
        getApplicationByIdQueryHandler = new GetApplicationByIdQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationByIdQuery_shouldReturnDTO_whenFound() {
        // Given
        Integer id = 1;
        GetApplicationByIdQuery query = new GetApplicationByIdQuery(id);

        Application application = new Application();
        application.setId(id);
        application.setName("Sample App");
        application.setCode("APP001");
        application.setType(AppType.INTERNAL);
        application.setStatus(Status.ACTIVE);
        application.setSlug("sample-app");

        Mockito.when(applicationRepository.findById(id)).thenReturn(Optional.of(application));

        // When
        ResponseEntity<ApplicationDTO> response = getApplicationByIdQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getId());
        assertEquals("Sample App", response.getBody().getName());
        assertEquals("APP001", response.getBody().getCode());
    }

    @Test
    void testHandleGetApplicationByIdQuery_shouldThrow_whenNotFound() {
        // Given
        Integer id = 999;
        GetApplicationByIdQuery query = new GetApplicationByIdQuery(id);

        Mockito.when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        // When / Then
        IgrpResponseStatusException exception = assertThrows(
                IgrpResponseStatusException.class,
                () -> getApplicationByIdQueryHandler.handle(query)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getProblem().getStatus());
        assertEquals("Application not found", exception.getProblem().getTitle());
    }
}
