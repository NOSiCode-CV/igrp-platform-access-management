package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
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
public class GetDepartmentPermissionsQueryHandlerTest {

  @Mock
  private PermissionEntityRepository permissionRepository;

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @Mock
  private PermissionMapper permissionMapper;

  @InjectMocks
  private GetDepartmentPermissionsQueryHandler handler;

  private DepartmentEntity department;
  private PermissionEntity permRead;
  private PermissionEntity permWrite;
  private PermissionDTO dtoRead;
  private PermissionDTO dtoWrite;

  @BeforeEach
  void setUp() {
    department = new DepartmentEntity();
    department.setId(1);
    department.setCode("DEP1");

    permRead = new PermissionEntity();
    permRead.setName("perm.read");
    permRead.setDepartments(new HashSet<>());

    permWrite = new PermissionEntity();
    permWrite.setName("perm.write");
    permWrite.setDepartments(new HashSet<>());

    dtoRead = new PermissionDTO();
    dtoRead.setName("perm.read");

    dtoWrite = new PermissionDTO();
    dtoWrite.setName("perm.write");
  }

  // ------------------ SUCCESS CASES ------------------

  @Test
  void testHandle_AllPermissions_NoFilter() {
    GetDepartmentPermissionsQuery query = new GetDepartmentPermissionsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(permissionRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(permRead, permWrite));
    when(permissionMapper.mapToDTO(permRead)).thenReturn(dtoRead);
    when(permissionMapper.mapToDTO(permWrite)).thenReturn(dtoWrite);

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(2, response.getBody().size());
    assertTrue(response.getBody().contains(dtoRead));
    assertTrue(response.getBody().contains(dtoWrite));
  }

  @Test
  void testHandle_FilterByPermissionName() {
    GetDepartmentPermissionsQuery query = new GetDepartmentPermissionsQuery("perm.read", "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(permissionRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of(permRead, permWrite));
    when(permissionMapper.mapToDTO(permRead)).thenReturn(dtoRead);

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("perm.read", response.getBody().get(0).getName());
  }

  @Test
  void testHandle_NoPermissionsAvailable() {
    GetDepartmentPermissionsQuery query = new GetDepartmentPermissionsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenReturn(department);
    when(permissionRepository.findByDepartmentAndStatusNotFiltered(department.getId(), Status.DELETED.getCode(), null))
            .thenReturn(List.of());

    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertTrue(response.getBody().isEmpty());
  }

  // ------------------ ERROR CASES ------------------

  @Test
  void testHandle_DepartmentNotFound_ShouldThrow() {
    GetDepartmentPermissionsQuery query = new GetDepartmentPermissionsQuery(null, "DEP1");

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEP1")).thenThrow(IgrpResponseStatusException.class);

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
  }
}