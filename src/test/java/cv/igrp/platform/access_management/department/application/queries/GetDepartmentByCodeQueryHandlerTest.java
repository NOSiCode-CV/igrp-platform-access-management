package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class GetDepartmentByCodeQueryHandlerTest {

  @Mock
  DepartmentEntityRepository departmentRepository;

  @Mock
  DepartmentMapper departmentMapper;

  @InjectMocks
  private GetDepartmentByCodeQueryHandler getDepartmentByCodeQueryHandler;

  private DepartmentEntity department;
  private DepartmentDTO departmentDTO;
  private GetDepartmentByCodeQuery query;

  private final String DEPARTMENT_CODE = "HR";

  private GetDepartmentByCodeQuery getDepartmentByCodeQuery(String departmentCode){
    return new GetDepartmentByCodeQuery(departmentCode);
  }

  @BeforeEach
  void setUp() {
    department = new DepartmentEntity();
    department.setCode(DEPARTMENT_CODE);
    department.setCode("HR");
    department.setName("Human Resources");

    departmentDTO = new DepartmentDTO();
    departmentDTO.setCode(DEPARTMENT_CODE);
    departmentDTO.setCode("HR");
    departmentDTO.setName("Human Resources");

    query = getDepartmentByCodeQuery(DEPARTMENT_CODE);
  }

  @Test
  @DisplayName("should return 200 OK with DepartmentDTO when department exists")
  void testHandle_whenDepartmentExists_shouldReturnOk() {
    // Arrange
    when(departmentRepository.findByCode(DEPARTMENT_CODE)).thenReturn(Optional.of(department));
    when(departmentMapper.toDto(department)).thenReturn(departmentDTO);

    // Act
    ResponseEntity<DepartmentDTO> response = getDepartmentByCodeQueryHandler.handle(query);

    // Assert
    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(departmentDTO, response.getBody());

    // Verify
    verify(departmentRepository, times(1)).findByCode(department.getCode());
    verify(departmentMapper, times(1)).toDto(department);
    verifyNoMoreInteractions(departmentRepository, departmentMapper);
  }

  @Test
  @DisplayName("should throw IgrpResponseStatusException when department not found")
  void handle_whenDepartmentNotFound_shouldThrowException() {
    // Arrange
    when(departmentRepository.findByCode(DEPARTMENT_CODE)).thenReturn(Optional.empty());

    // Act
    IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
            getDepartmentByCodeQueryHandler.handle(query));

    // Assert
    assertNotNull(exception);

    assertNotNull(exception.getBody().getProperties());
    assertEquals("Department not found with code: " + DEPARTMENT_CODE, exception.getBody().getProperties().get("details"));

    // Verify
    verify(departmentRepository, times(1)).findByCode(DEPARTMENT_CODE);
    verifyNoMoreInteractions(departmentRepository, departmentMapper);
  }

  @Test
  @DisplayName("should throw NullPointerException when mapper returns null")
  void testHandle_whenMapperReturnsNull_shouldThrowException() {
    // Arrange
    when(departmentRepository.findByCode(DEPARTMENT_CODE)).thenReturn(Optional.of(department));
    when(departmentMapper.toDto(department)).thenReturn(null);

    // Act
    NullPointerException exception = assertThrows(NullPointerException.class, () ->
            getDepartmentByCodeQueryHandler.handle(query));

    // Assert
    assertTrue(exception.getMessage().contains("\"dto\" is null"));
  }

}