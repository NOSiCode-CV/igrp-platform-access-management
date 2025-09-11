package cv.igrp.platform.access_management.role.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetRolesByNameQueryHandlerTest {

  @InjectMocks
  private GetRolesByNameQueryHandler underTest;
  @Mock
  private RoleEntityRepository roleRepository;
  @Mock
  private RoleMapper roleMapper;

  @Test
  void itShouldStartContext() {
    assertNotNull(underTest);
  }

  @Test
  void itShouldThrowRecordNotFoundException_When_ProvidedRoleName_NotFound() {
    //... Given
    String roleName = "test";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleName);

    when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
            .thenReturn(Optional.empty());

    //... When
    IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
  }

  @Test
  void itShouldNotCallMapper_WhenRoleNotFound() {
    //... Given
    String roleName = "admin";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleName);
    RoleEntity savedRole = new RoleEntity();
    String roleDesc = "RoleName";
    savedRole.setName(roleName);
    savedRole.setDescription(roleDesc);
    Status roleStatus = Status.ACTIVE;
    savedRole.setStatus(roleStatus);
    RoleDTO expectedDto = new RoleDTO();
    expectedDto.setName(roleName);
    expectedDto.setDescription(roleDesc);
    expectedDto.setStatus(roleStatus);
    when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
            .thenReturn(Optional.empty());

    //... When
    IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());

    verify(roleRepository, times(1)).findByNameAndStatusNot(roleName, Status.DELETED);
    verify(roleMapper, times(0)).mapToDto(savedRole);
  }

  @Test
  void itShouldReturnRoleDTO_WhenRoleExists() {
    //... Given
    String roleName = "admin";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleName);
    RoleEntity savedRole = new RoleEntity();
    String roleDesc = "RoleName";
    savedRole.setName(roleName);
    savedRole.setDescription(roleDesc);
    Status roleStatus = Status.ACTIVE;
    savedRole.setStatus(roleStatus);
    RoleDTO expectedDto = new RoleDTO();
    expectedDto.setName(roleName);
    expectedDto.setDescription(roleDesc);
    expectedDto.setStatus(roleStatus);
    when(roleRepository.findByNameAndStatusNot(roleName, Status.DELETED))
            .thenReturn(Optional.of(savedRole));
    when(roleMapper.mapToDto(savedRole))
            .thenReturn(expectedDto);

    //... When
    ResponseEntity<RoleDTO> response = underTest.handle(query);

    //... Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(roleName, response.getBody().getName());
    assertNotNull(response.getBody());
    assertEquals(expectedDto.getName(), response.getBody().getName());

    verify(roleRepository, times(1)).findByNameAndStatusNot(roleName, Status.DELETED);
    verify(roleMapper, times(1)).mapToDto(savedRole);
  }

}
