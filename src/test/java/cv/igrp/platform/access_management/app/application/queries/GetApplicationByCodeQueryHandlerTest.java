package cv.igrp.platform.access_management.app.application.queries;

import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.application.dto.ApplicationDTO;
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

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetApplicationByCodeQueryHandlerTest {
  @Mock
  private ApplicationEntityRepository applicationRepository;

  @Mock
  private final ApplicationMapper applicationMapper = Mockito.mock(ApplicationMapper.class);

  private GetApplicationByCodeQueryHandler getApplicationByCodeQueryHandler;

  @BeforeEach
  void setUp() {
    getApplicationByCodeQueryHandler = new GetApplicationByCodeQueryHandler(applicationRepository, applicationMapper);
  }

  @Test
  void testHandleGetApplicationByCodeQuery_shouldReturnDTO_whenFound() {
    // Given
    String code = "APP001";
    GetApplicationByCodeQuery query = new GetApplicationByCodeQuery(code);

    ApplicationEntity application = new ApplicationEntity();
    application.setCode(code);
    application.setName("Sample App");
    application.setCode("APP001");
    application.setType(AppType.INTERNAL);
    application.setStatus(Status.ACTIVE);
    application.setSlug("sample-app");

    Mockito.when(applicationRepository.findByCode(code)).thenReturn(Optional.of(application));

    // When
    ResponseEntity<ApplicationDTO> response = getApplicationByCodeQueryHandler.handle(query);

    // Then
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(code, response.getBody().getCode());
    assertEquals("Sample App", response.getBody().getName());
    assertEquals("APP001", response.getBody().getCode());
  }

  @Test
  void testHandleGetApplicationByCodeQuery_shouldThrow_whenNotFound() {
    // Given
    String code = "APP";
    GetApplicationByCodeQuery query = new GetApplicationByCodeQuery(code);

    Mockito.when(applicationRepository.findByCode(code)).thenReturn(Optional.empty());

    // When / Then
    IgrpResponseStatusException exception = assertThrows(
            IgrpResponseStatusException.class,
            () -> getApplicationByCodeQueryHandler.handle(query)
    );

    assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());
    assertEquals("Application not found", exception.getBody().getTitle());
  }

}