package cv.igrp.platform.access_management.app.application.queries;


import static org.junit.jupiter.api.Assertions.*;
import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.AppType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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
import cv.igrp.platform.access_management.app.application.dto.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetApplicationByIdQueryHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    private GetApplicationByIdQueryHandler getApplicationByIdQueryHandler;

    @Mock
    private final ApplicationMapper applicationMapper = Mockito.mock(ApplicationMapper.class);

    @BeforeEach
    void setUp() {
        getApplicationByIdQueryHandler = new GetApplicationByIdQueryHandler(applicationRepository, applicationMapper);
    }

    @Test
    void testHandleGetApplicationByIdQuery_shouldReturnDTO_whenFound() {
        // Given
        Integer id = 1;
        GetApplicationByIdQuery query = new GetApplicationByIdQuery(id);

        ApplicationEntity application = new ApplicationEntity();
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
        assertNotNull(response.getBody());
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

        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
        assertEquals("Application not found", exception.getBody().getTitle());
    }
}
