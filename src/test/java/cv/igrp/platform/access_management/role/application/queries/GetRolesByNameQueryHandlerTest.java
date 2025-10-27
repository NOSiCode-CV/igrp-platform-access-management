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
    String roleCode = "test";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleCode);

    when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
            .thenReturn(Optional.empty());

    //... When
    IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
  }

  @Test
  void itShouldNotCallMapper_WhenRoleNotFound() {
    //... Given
    String roleCode = "admin";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleCode);
    RoleEntity savedRole = new RoleEntity();
    String roleDesc = "RoleName";
    savedRole.setCode(roleCode);
    savedRole.setDescription(roleDesc);
    Status roleStatus = Status.ACTIVE;
    savedRole.setStatus(roleStatus);
    RoleDTO expectedDto = new RoleDTO();
    expectedDto.setCode(roleCode);
    expectedDto.setDescription(roleDesc);
    expectedDto.setStatus(roleStatus);
    when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
            .thenReturn(Optional.empty());

    //... When
    IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

    //... Then
    assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());

    verify(roleRepository, times(1)).findByCodeAndStatusNot(roleCode, Status.DELETED);
    verify(roleMapper, times(0)).mapToDto(savedRole);
  }

  @Test
  void itShouldReturnRoleDTO_WhenRoleExists() {
    //... Given
    String roleCode = "admin";
    GetRolesByNameQuery query = new GetRolesByNameQuery(roleCode);
    RoleEntity savedRole = new RoleEntity();
    String roleDesc = "RoleName";
    savedRole.setCode(roleCode);
    savedRole.setDescription(roleDesc);
    Status roleStatus = Status.ACTIVE;
    savedRole.setStatus(roleStatus);
    RoleDTO expectedDto = new RoleDTO();
    expectedDto.setCode(roleCode);
    expectedDto.setDescription(roleDesc);
    expectedDto.setStatus(roleStatus);
    when(roleRepository.findByCodeAndStatusNot(roleCode, Status.DELETED))
            .thenReturn(Optional.of(savedRole));
    when(roleMapper.mapToDto(savedRole))
            .thenReturn(expectedDto);

    //... When
    ResponseEntity<RoleDTO> response = underTest.handle(query);

    //... Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(roleCode, response.getBody().getCode());
    assertNotNull(response.getBody());
    assertEquals(expectedDto.getCode(), response.getBody().getCode());

    verify(roleRepository, times(1)).findByCodeAndStatusNot(roleCode, Status.DELETED);
    verify(roleMapper, times(1)).mapToDto(savedRole);
  }

}
