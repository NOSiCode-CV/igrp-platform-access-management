package cv.igrp.platform.access_management.permission.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetPermissionByNameQueryHandlerTest {

  @InjectMocks
  private GetPermissionByNameQueryHandler underTest;
  @Mock
  private PermissionEntityRepository permissionRepository;
  @Mock
  private PermissionMapper permissionMapper;

  @Test
  void itShouldStartContext() {
    assertNotNull(underTest);
  }

  @Test
  void itShouldThrowRecordNotFoundException_WhenProvidedName_DoesNotExist() {
    //... Given
    String permissionName = "test";
    GetPermissionByNameQuery query = new GetPermissionByNameQuery(permissionName);

    when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
            .thenReturn(Optional.empty());

    //... When
    IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    verifyNoInteractions(permissionMapper);
  }

  @Test
  void itShouldReturnPermissionDTO_WhenPermissionExists() {
    // Given
    String permissionName = "test";
    GetPermissionByNameQuery query = new GetPermissionByNameQuery(permissionName);

    PermissionEntity permission = new PermissionEntity();
    permission.setName(permissionName);
    permission.setName("READ_USERS");
    permission.setStatus(Status.ACTIVE);

    PermissionDTO expectedDTO = new PermissionDTO();
    expectedDTO.setName(permissionName);
    expectedDTO.setName("READ_USERS");
    expectedDTO.setStatus(Status.ACTIVE);

    // Stubbing
    when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED))
            .thenReturn(Optional.of(permission));
    when(permissionMapper.mapToDTO(permission)).thenReturn(expectedDTO);

    // When
    ResponseEntity<PermissionDTO> response = underTest.handle(query);

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(expectedDTO, response.getBody());

    verify(permissionRepository).findByNameAndStatusNot(permissionName, Status.DELETED);
    verify(permissionMapper).mapToDTO(permission);
  }

}