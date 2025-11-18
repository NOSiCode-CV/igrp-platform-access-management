package cv.igrp.platform.access_management.department.application.queries;

import cv.igrp.platform.access_management.role.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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

import static cv.igrp.platform.access_management.shared.infrastructure.service.ConfigurationService.IGRP_PERMISSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailablePermissionsForRolesQueryHandlerTest {

  @InjectMocks
  private GetAvailablePermissionsForRolesQueryHandler handler;

  @Mock
  private PermissionEntityRepository permissionRepository;

  @Mock
  private PermissionMapper permissionMapper;

  private PermissionEntity permissionEntity1;
  private PermissionEntity permissionEntity2;
  private PermissionDTO permissionDTO1;
  private PermissionDTO permissionDTO2;

  @BeforeEach
  void setUp() {
    // Initialize mock entities and DTOs
    permissionEntity1 = new PermissionEntity();
    permissionEntity1.setId(1);
    permissionEntity1.setName("permission_view_a");
    permissionEntity1.setDescription("View A");

    permissionEntity2 = new PermissionEntity();
    permissionEntity2.setId(2);
    permissionEntity2.setName("permission_edit_b");
    permissionEntity2.setDescription("Edit B");

    permissionDTO1 = new PermissionDTO();
    permissionDTO1.setId(1);
    permissionDTO1.setName("permission_view_a");
    permissionDTO1.setDescription("View A");

    permissionDTO2 = new PermissionDTO();
    permissionDTO2.setId(2);
    permissionDTO2.setName("permission_edit_b");
    permissionDTO2.setDescription("Edit B");
  }

  @Test
  void testHandle_ReturnsListOfAvailablePermissions() {
    // Given a query and expected data
    String roleCode = "ADMIN";
    GetAvailablePermissionsForRolesQuery query = new GetAvailablePermissionsForRolesQuery(roleCode, "DEPT");
    List<PermissionEntity> mockEntities = List.of(permissionEntity1, permissionEntity2);

    // Mock repository and mapper behavior
    when(permissionRepository.findAvailablePermissionsForRole("DEPT", IGRP_PERMISSION)).thenReturn(mockEntities);
    when(permissionMapper.mapToDTO(permissionEntity1)).thenReturn(permissionDTO1);
    when(permissionMapper.mapToDTO(permissionEntity2)).thenReturn(permissionDTO2);

    // When the handler is called
    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(permissionDTO1.getName(), response.getBody().get(0).getName());
    assertEquals(permissionDTO2.getName(), response.getBody().get(1).getName());
  }

  @Test
  void testHandle_NoPermissionsFound_ReturnsEmptyList() {
    // Given a query for a role with no available permissions
    String roleCode = "USER";
    GetAvailablePermissionsForRolesQuery query = new GetAvailablePermissionsForRolesQuery(roleCode, "DEPT");

    // Mock repository to return an empty list
    when(permissionRepository.findAvailablePermissionsForRole("DEPT", IGRP_PERMISSION)).thenReturn(Collections.emptyList());

    // When the handler is called
    ResponseEntity<List<PermissionDTO>> response = handler.handle(query);

    // Then verify the response
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().size());
  }
}