package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableApplicationsForDepartmentQueryHandlerTest {

  @InjectMocks
  private GetAvailableApplicationsForDepartmentQueryHandler handler;

  @Mock
  private ApplicationEntityRepository applicationEntityRepository;

  @Mock
  private ApplicationMapper applicationMapper;

  private ApplicationEntity appEntity1;
  private ApplicationEntity appEntity2;
  private ApplicationDTO appDTO1;
  private ApplicationDTO appDTO2;

  @BeforeEach
  void setUp() {
    // Initialize mock entities and DTOs
    appEntity1 = new ApplicationEntity();
    appEntity1.setId(1);
    appEntity1.setCode("APP_A");
    appEntity1.setName("Application A");

    appEntity2 = new ApplicationEntity();
    appEntity2.setId(2);
    appEntity2.setCode("APP_B");
    appEntity2.setName("Application B");

    appDTO1 = new ApplicationDTO();
    appDTO1.setId(1);
    appDTO1.setCode("APP_A");
    appDTO1.setName("Application A");

    appDTO2 = new ApplicationDTO();
    appDTO2.setId(2);
    appDTO2.setCode("APP_B");
    appDTO2.setName("Application B");
  }

  @Test
  void testHandle_ReturnsListOfAvailableApplications() {
    // Given a query and expected data
    String departmentCode = "DEPT_1";
    GetAvailableApplicationsForDepartmentQuery query = new GetAvailableApplicationsForDepartmentQuery(departmentCode);
    List<ApplicationEntity> mockEntities = List.of(appEntity1, appEntity2);

    // Mock repository and mapper behavior
    when(applicationEntityRepository.findAvailableApplicationsForDepartment(departmentCode)).thenReturn(mockEntities);
    when(applicationMapper.toDto(appEntity1)).thenReturn(appDTO1);
    when(applicationMapper.toDto(appEntity2)).thenReturn(appDTO2);

    // When the handler is called
    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(appDTO1.getName(), response.getBody().get(0).getName());
    assertEquals(appDTO2.getName(), response.getBody().get(1).getName());
  }

  @Test
  void testHandle_NoApplicationsFound_ReturnsEmptyList() {
    // Given a query for a department with no available applications
    String departmentCode = "DEPT_2";
    GetAvailableApplicationsForDepartmentQuery query = new GetAvailableApplicationsForDepartmentQuery(departmentCode);

    // Mock repository to return an empty list
    when(applicationEntityRepository.findAvailableApplicationsForDepartment(departmentCode)).thenReturn(Collections.emptyList());

    // When the handler is called
    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().size());
  }
}