package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetPermissionsByRoleIdQueryHandlerTest {

  @InjectMocks
  private GetPermissionsByRoleIdQueryHandler underTest;
  @Mock
  private RoleEntityRepository roleRepository;
  @Mock
  private DepartmentEntityRepository departmentRepository;
  @Mock
  private PermissionMapper permissionMapper;


  @Test
  void itShouldStartContext() {
    assertNotNull(underTest);
  }

  @Test
  void itShouldThrowRecordNotFoundException_WhenProvidedRoleId_NotFound() {
    //... Given
    String roleCode = "admin";

    DepartmentEntity department = new DepartmentEntity();
    department.setCode("DEPT_IGRP");
    department.setStatus(DepartmentStatus.ACTIVE);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_IGRP")).thenReturn(department);
    when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED))
            .thenReturn(Optional.empty());

    GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery("DEPT_IGRP", roleCode);

    // When
    IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
            () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
  }

  @Test
  void itShouldCallMapperOnlyForValidPermissions() {
    // Given
    int roleId = 1;
    String roleCode = "admin";
    GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery( "DEPT_IGRP", roleCode);

    PermissionEntity activePermission = new PermissionEntity();
    activePermission.setId(1);
    activePermission.setName("Read");
    activePermission.setStatus(Status.ACTIVE);

    PermissionEntity deletedPermission = new PermissionEntity();
    deletedPermission.setId(2);
    deletedPermission.setName("Write");
    deletedPermission.setStatus(Status.DELETED);

    Set<PermissionEntity> permissions = new HashSet<>(List.of(activePermission, deletedPermission));

    RoleEntity role = new RoleEntity();
    role.setId(roleId);
    role.setStatus(Status.ACTIVE);
    role.setPermissions(permissions);

    DepartmentEntity department = new DepartmentEntity();
    department.setCode("DEPT_IGRP");
    department.setStatus(DepartmentStatus.ACTIVE);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_IGRP")).thenReturn(department);
    when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));

    PermissionDTO activePermissionDTO = new PermissionDTO();
    activePermissionDTO.setId(1);
    when(permissionMapper.mapToDTO(activePermission)).thenReturn(activePermissionDTO);

    // When
    ResponseEntity<List<PermissionDTO>> result = underTest.handle(query);

    // Then
    assertNotNull(result.getBody());
    assertEquals(1, result.getBody().size());
    assertEquals(activePermissionDTO, result.getBody().get(0));

    verify(permissionMapper).mapToDTO(activePermission);
    verify(permissionMapper, never()).mapToDTO(deletedPermission);
  }

  @Test
  void itShouldNotReturnDeletedPermissions_EvenIfTheyAreInTheRole() {
    // Given
    int roleId = 1;
    String roleCode = "admin";
    GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery( "DEPT_IGRP", roleCode);

    PermissionEntity deletedPermission1 = new PermissionEntity();
    deletedPermission1.setId(1);
    deletedPermission1.setName("Permission A");
    deletedPermission1.setStatus(Status.DELETED);

    PermissionEntity deletedPermission2 = new PermissionEntity();
    deletedPermission2.setId(2);
    deletedPermission2.setName("Permission B");
    deletedPermission2.setStatus(Status.DELETED);

    RoleEntity role = new RoleEntity();
    role.setId(roleId);
    role.setStatus(Status.ACTIVE);
    role.setPermissions(new HashSet<>(List.of(deletedPermission1, deletedPermission2)));

    DepartmentEntity department = new DepartmentEntity();
    department.setCode("DEPT_IGRP");
    department.setStatus(DepartmentStatus.ACTIVE);

    when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT_IGRP")).thenReturn(department);
    when(roleRepository.findByDepartmentAndCodeAndStatusNot(department, roleCode, Status.DELETED)).thenReturn(Optional.of(role));

    // When
    ResponseEntity<List<PermissionDTO>> result = underTest.handle(query);

    // Then
    assertNotNull(result);
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertNotNull(result.getBody());
    assertTrue(result.getBody().isEmpty());

    verify(permissionMapper, never()).mapToDTO(any());
  }
}
