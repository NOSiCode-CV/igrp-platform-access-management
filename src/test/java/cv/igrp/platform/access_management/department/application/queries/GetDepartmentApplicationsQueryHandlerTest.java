package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class GetDepartmentApplicationsQueryHandlerTest {

  @Mock
  private ApplicationEntityRepository applicationRepository;

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @Mock
  private ApplicationMapper applicationMapper;

  @InjectMocks
  private GetDepartmentApplicationsQueryHandler handler;

  private DepartmentEntity department;
  private ApplicationEntity app1;
  private ApplicationEntity app2;
  private ApplicationDTO dto1;
  private ApplicationDTO dto2;

  @BeforeEach
  void setUp() {
    department = new DepartmentEntity();
    department.setId(1);
    department.setCode("DEP1");

    app1 = new ApplicationEntity();
    app1.setCode("APP1");

    app2 = new ApplicationEntity();
    app2.setCode("APP2");

    dto1 = new ApplicationDTO();
    dto1.setCode("APP1");

    dto2 = new ApplicationDTO();
    dto2.setCode("APP2");
  }

  // ------------------ SUCCESS CASES ------------------

  @Test
  void testHandle_AllApplications_NoFilter() {
    GetDepartmentApplicationsQuery query = new GetDepartmentApplicationsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(applicationRepository.findByDepartmentAndStatusNotFiltered(department, Status.DELETED, null))
            .thenReturn(List.of(app1, app2));
    when(applicationMapper.toDto(app1)).thenReturn(dto1);
    when(applicationMapper.toDto(app2)).thenReturn(dto2);

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().contains(dto1));
    assertTrue(response.getBody().contains(dto2));
  }

  @Test
  void testHandle_FilterByApplicationCode() {
    GetDepartmentApplicationsQuery query = new GetDepartmentApplicationsQuery("APP1",  "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(applicationRepository.findByDepartmentAndStatusNotFiltered(department, Status.DELETED, null))
            .thenReturn(List.of(app1, app2));
    when(applicationMapper.toDto(app1)).thenReturn(dto1);

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("APP1", response.getBody().get(0).getCode());
  }

  @Test
  void testHandle_NoApplicationsAvailable() {
    GetDepartmentApplicationsQuery query = new GetDepartmentApplicationsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(applicationRepository.findByDepartmentAndStatusNotFiltered(department, Status.DELETED, null))
            .thenReturn(List.of());

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertTrue(response.getBody().isEmpty());
  }

  // ------------------ ERROR CASES ------------------

  @Test
  void testHandle_DepartmentNotFound_ShouldThrow() {
    GetDepartmentApplicationsQuery query = new GetDepartmentApplicationsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenThrow(IgrpResponseStatusException.class);

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
  }
}