package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.department.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;

@ExtendWith(MockitoExtension.class)
public class GetDepartmentResourcesQueryHandlerTest {

  @Mock
  private ResourceEntityRepository resourceRepository;

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @Mock
  private ResourceMapper resourceMapper;

  @InjectMocks
  private GetDepartmentResourcesQueryHandler handler;

  private DepartmentEntity department;
  private ResourceEntity resourceRead;
  private ResourceEntity resourceWrite;
  private ResourceDTO dtoRead;
  private ResourceDTO dtoWrite;

  @BeforeEach
  void setUp() {
    department = new DepartmentEntity();
    department.setId(1);
    department.setCode("DEP1");

    resourceRead = new ResourceEntity();
    resourceRead.setName("resource.read");
    resourceRead.setDepartments(new HashSet<>());

    resourceWrite = new ResourceEntity();
    resourceWrite.setName("resource.write");
    resourceWrite.setDepartments(new HashSet<>());

    dtoRead = new ResourceDTO();
    dtoRead.setName("resource.read");

    dtoWrite = new ResourceDTO();
    dtoWrite.setName("resource.write");
  }

  // ------------------ SUCCESS CASES ------------------

  @Test
  void testHandle_AllResources_NoFilter() {
    GetDepartmentResourcesQuery query = new GetDepartmentResourcesQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(resourceRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(resourceRead, resourceWrite));
    when(resourceMapper.toDto(resourceRead)).thenReturn(dtoRead);
    when(resourceMapper.toDto(resourceWrite)).thenReturn(dtoWrite);

    ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().contains(dtoRead));
    assertTrue(response.getBody().contains(dtoWrite));
  }

  @Test
  void testHandle_FilterByResourceName() {
    GetDepartmentResourcesQuery query = new GetDepartmentResourcesQuery("resource.read", "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(resourceRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(resourceRead, resourceWrite));
    when(resourceMapper.toDto(resourceRead)).thenReturn(dtoRead);

    ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("resource.read", response.getBody().get(0).getName());
  }

  @Test
  void testHandle_NoResourcesAvailable() {
    GetDepartmentResourcesQuery query = new GetDepartmentResourcesQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(resourceRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of());

    ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertTrue(response.getBody().isEmpty());
  }

  // ------------------ ERROR CASES ------------------

  @Test
  void testHandle_DepartmentNotFound_ShouldThrow() {
    GetDepartmentResourcesQuery query = new GetDepartmentResourcesQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenThrow(IgrpResponseStatusException.class);

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
    // Replace RuntimeException with actual exception type if your service throws a specific one
  }
}