package cv.igrp.platform.access_management.department.application.queries;

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_PERMISSION;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class GetAvailablePermissionsForDepartmentQueryHandlerTest {

  @Mock
  private PermissionMapper permissionMapper;

  @Mock
  private PermissionEntityRepository permissionRepository;

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @InjectMocks
  private GetAvailablePermissionsForDepartmentQueryHandler handler;

  private PermissionEntity perm1;
  private PermissionEntity perm2;
  private DepartmentEntity dept1;
  private ResourceEntity res1;
  private ResourceEntity res2;
  private PermissionDTO dto1;
  private PermissionDTO dto2;

  @BeforeEach
  void setUp() {

    dept1 = new DepartmentEntity();
    dept1.setCode("DEP1");
    dept1.setName("Department1");
    dept1.setStatus(DepartmentStatus.ACTIVE);

    res1 = new ResourceEntity();
    res1.setName("Resource1");
    res1.setDepartments(Set.of(dept1));
    res1.setStatus(Status.ACTIVE);

    perm1 = new PermissionEntity();
    perm1.setName("perm.read");
    perm1.setResources(Set.of(res1));
    perm1.setStatus(Status.ACTIVE);

    res2 = new ResourceEntity();
    res2.setName("Resource2");
    res2.setDepartments(Set.of(dept1));
    res2.setStatus(Status.ACTIVE);

    perm2 = new PermissionEntity();
    perm2.setName("perm.write");
    perm2.setResources(Set.of(res2));
    perm2.setStatus(Status.ACTIVE);

    dto1 = new PermissionDTO();
    dto1.setName("perm.read");

    dto2 = new PermissionDTO();
    dto2.setName("perm.write");
  }

  // ------------------ SUCCESS CASES ------------------

  @Test
  void testHandle_AllPermissionsAvailable_NoResourceFilter() {
    GetAvailablePermissionsForDepartmentQuery query = new GetAvailablePermissionsForDepartmentQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(dept1);
    when(permissionRepository.findAvailablePermissionsForDepartment("DEP1", IGRP_PERMISSION)).thenReturn(List.of(perm1, perm2));
    when(permissionMapper.mapToDTO(perm1)).thenReturn(dto1);
    when(permissionMapper.mapToDTO(perm2)).thenReturn(dto2);

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().contains(dto1));
    assertTrue(response.getBody().contains(dto2));
  }

  @Test
  void testHandle_FilterByResourceName() {
    GetAvailablePermissionsForDepartmentQuery query = new GetAvailablePermissionsForDepartmentQuery("Resource1", "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(dept1);
    when(permissionRepository.findAvailablePermissionsForDepartment("DEP1", IGRP_PERMISSION)).thenReturn(List.of(perm1, perm2));
    when(permissionMapper.mapToDTO(perm1)).thenReturn(dto1);

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("perm.read", response.getBody().get(0).getName());
  }

  @Test
  void testHandle_NoPermissionsAvailable() {
    GetAvailablePermissionsForDepartmentQuery query = new GetAvailablePermissionsForDepartmentQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(dept1);
    when(permissionRepository.findAvailablePermissionsForDepartment("DEP1", IGRP_PERMISSION)).thenReturn(List.of());

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertTrue(response.getBody().isEmpty());
  }

  // ------------------ ERROR CASES ------------------

  @Test
  void testHandle_DepartmentNotFound_ShouldThrow() {
    GetAvailablePermissionsForDepartmentQuery query = new GetAvailablePermissionsForDepartmentQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenThrow(IgrpResponseStatusException.class);

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
  }
}
