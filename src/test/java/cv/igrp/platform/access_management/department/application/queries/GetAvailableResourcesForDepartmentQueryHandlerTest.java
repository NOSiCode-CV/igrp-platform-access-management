package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.ResourceDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_RESOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableResourcesForDepartmentQueryHandlerTest {

  @InjectMocks
  private GetAvailableResourcesForDepartmentQueryHandler handler;

  @Mock
  private ResourceEntityRepository resourceEntityRepository;

  @Mock
  private DepartmentEntityRepository departmentEntityRepository;

  @Mock
  private ResourceMapper resourceMapper;

  private ResourceEntity resourceEntity1;
  private ResourceEntity resourceEntity2;
  private ResourceDTO resourceDTO1;
  private ResourceDTO resourceDTO2;

  @BeforeEach
  void setUp() {
    // Initialize mock entities and DTOs
    resourceEntity1 = new ResourceEntity();
    resourceEntity1.setId(1);
    resourceEntity1.setName("RES_A");
    resourceEntity1.setDescription("Resource A");

    resourceEntity2 = new ResourceEntity();
    resourceEntity2.setId(2);
    resourceEntity2.setName("RES_B");
    resourceEntity2.setDescription("Resource B");

    resourceDTO1 = new ResourceDTO();
    resourceDTO1.setId(1);
    resourceDTO1.setName("RES_A");
    resourceDTO1.setDescription("Resource A");

    resourceDTO2 = new ResourceDTO();
    resourceDTO2.setId(2);
    resourceDTO2.setName("RES_B");
    resourceDTO2.setDescription("Resource B");
  }

  @Test
  void testHandle_ReturnsListOfAvailableResources() {
    // Given a query and expected data
    String departmentCode = "DEPT_1";
    GetAvailableResourcesForDepartmentQuery query = new GetAvailableResourcesForDepartmentQuery(departmentCode);
    List<ResourceEntity> mockEntities = List.of(resourceEntity1, resourceEntity2);

    // Mock repository and mapper behavior
    when(departmentEntityRepository.findByCodeAndStatusNotDeleted(departmentCode)).thenReturn(new DepartmentEntity());
    when(resourceEntityRepository.findAvailableResourcesForDepartment(departmentCode, IGRP_RESOURCE)).thenReturn(mockEntities);
    when(resourceMapper.toDto(resourceEntity1)).thenReturn(resourceDTO1);
    when(resourceMapper.toDto(resourceEntity2)).thenReturn(resourceDTO2);

    // When the handler is called
    ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(resourceDTO1.getName(), response.getBody().get(0).getName());
    assertEquals(resourceDTO2.getName(), response.getBody().get(1).getName());
  }

  @Test
  void testHandle_NoResourcesFound_ReturnsEmptyList() {
    // Given a query for a department with no available resources
    String departmentCode = "DEPT_2";
    GetAvailableResourcesForDepartmentQuery query = new GetAvailableResourcesForDepartmentQuery(departmentCode);

    // Mock repository to return an empty list
    when(departmentEntityRepository.findByCodeAndStatusNotDeleted(departmentCode)).thenReturn(new DepartmentEntity());
    when(resourceEntityRepository.findAvailableResourcesForDepartment(departmentCode, IGRP_RESOURCE)).thenReturn(Collections.emptyList());

    // When the handler is called
    ResponseEntity<List<ResourceDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().size());
  }
}
