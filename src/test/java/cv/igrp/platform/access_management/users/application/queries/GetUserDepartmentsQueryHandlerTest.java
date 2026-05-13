package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetUserDepartmentsQueryHandlerTest {

  @Mock
  private DepartmentEntityRepository departmentRepository;

  @Mock
  private IGRPUserEntityRepository userRepository;

  @Mock
  private DepartmentMapper departmentMapper;

  @InjectMocks
  private GetUserDepartmentsQueryHandler handler;

  // -------------------------------------------------------------------------
  // SUCCESS SCENARIO: USER FOUND + DEPARTMENTS RETURNED
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldReturnDepartments_whenUserExists() {

    GetUserDepartmentsQuery query = new GetUserDepartmentsQuery("DEP", "00000000-0000-0000-0000-000000000001");

    IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000001");

    DepartmentEntity dep1 = new DepartmentEntity();
    dep1.setCode("DEP_A");

    DepartmentDTO dto1 = new DepartmentDTO();
    dto1.setCode("DEP_A");

    when(userRepository.findById("00000000-0000-0000-0000-000000000001")).thenReturn(Optional.of(mockUser));
    when(departmentRepository.findByUserAndNotDeletedFiltered(any(), any()))
            .thenReturn(List.of(dep1));
    when(departmentMapper.toDto(dep1)).thenReturn(dto1);

    ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());
    assertEquals("DEP_A", response.getBody().get(0).getCode());

    verify(userRepository).findById("00000000-0000-0000-0000-000000000001");
    verify(departmentRepository).findByUserAndNotDeletedFiltered(any(), any());
    verify(departmentMapper).toDto(dep1);
  }

  // -------------------------------------------------------------------------
  // SUCCESS SCENARIO: FILTERING WORKS
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldFilterDepartmentsByCode() {

    GetUserDepartmentsQuery query = new GetUserDepartmentsQuery("FIN", "00000000-0000-0000-0000-000000000001");

    IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000001");

    DepartmentEntity dep1 = new DepartmentEntity();
    dep1.setCode("FIN_DEPT");

    DepartmentEntity dep2 = new DepartmentEntity();
    dep2.setCode("HR_DEPT");

    DepartmentDTO dto1 = new DepartmentDTO();
    dto1.setCode("FIN_DEPT");

    when(userRepository.findById("00000000-0000-0000-0000-000000000001")).thenReturn(Optional.of(mockUser));
    when(departmentRepository.findByUserAndNotDeletedFiltered(any(), any()))
            .thenReturn(List.of(dep1, dep2)); // Only dep1 should pass the filter

    when(departmentMapper.toDto(dep1)).thenReturn(dto1);

    ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());
    assertEquals("FIN_DEPT", response.getBody().get(0).getCode());

    verify(departmentMapper, times(1)).toDto(dep1);
    verify(departmentMapper, never()).toDto(dep2);
  }

  // -------------------------------------------------------------------------
  // SUCCESS SCENARIO: NO DEPARTMENTS FOUND
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldReturnEmptyList_whenUserHasNoDepartments() {

    GetUserDepartmentsQuery query = new GetUserDepartmentsQuery(null, "00000000-0000-0000-0000-000000000001");

    IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000001");

    when(userRepository.findById("00000000-0000-0000-0000-000000000001")).thenReturn(Optional.of(mockUser));
    when(departmentRepository.findByUserAndNotDeletedFiltered(any(), any()))
            .thenReturn(List.of());

    ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }

  // -------------------------------------------------------------------------
  // ERROR SCENARIO: USER NOT FOUND → UNAUTHORIZED
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldThrowUnauthorized_whenUserNotFound() {

    GetUserDepartmentsQuery query = new GetUserDepartmentsQuery(null, "00000000-0000-0000-0000-000000000001");

    when(userRepository.findById("00000000-0000-0000-0000-000000000001")).thenReturn(Optional.empty());

    IgrpResponseStatusException ex = assertThrows(
            IgrpResponseStatusException.class,
            () -> handler.handle(query)
    );

    assertEquals("User not found", ex.getBody().getTitle());
    assertNotNull(ex.getMessage());
  }
}