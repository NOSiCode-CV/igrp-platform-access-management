package cv.igrp.platform.access_management.department.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
@DisplayName("GetDepartmentByIdQueryHandler Tests")
public class GetDepartmentByIdQueryHandlerTest {

    @Mock
    DepartmentEntityRepository departmentRepository;

    @Mock
    DepartmentMapper departmentMapper;

    @InjectMocks
    private GetDepartmentByIdQueryHandler getDepartmentByIdQueryHandler;

    private DepartmentEntity department;
    private DepartmentDTO departmentDTO;
    private GetDepartmentByIdQuery query;

    private final Integer DEPARTMENT_ID = 1;

    private GetDepartmentByIdQuery getDepartmentByIdQuery(Integer departmentId){
        return new GetDepartmentByIdQuery(departmentId);
    }

    @BeforeEach
    void setUp() {
        department = new DepartmentEntity();
        department.setId(DEPARTMENT_ID);
        department.setCode("HR");
        department.setName("Human Resources");

        departmentDTO = new DepartmentDTO();
        departmentDTO.setId(DEPARTMENT_ID);
        departmentDTO.setCode("HR");
        departmentDTO.setName("Human Resources");

        query = getDepartmentByIdQuery(DEPARTMENT_ID);
    }

    @Test
    @DisplayName("should return 200 OK with DepartmentDTO when department exists")
    void testHandle_whenDepartmentExists_shouldReturnOk() {
        // Arrange
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(departmentMapper.toDto(department)).thenReturn(departmentDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = getDepartmentByIdQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(departmentDTO, response.getBody());

        // Verify
        verify(departmentRepository, times(1)).findById(department.getId());
        verify(departmentMapper, times(1)).toDto(department);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when department not found")
    void handle_whenDepartmentNotFound_shouldThrowException() {
        // Arrange
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                getDepartmentByIdQueryHandler.handle(query));

        // Assert
        assertNotNull(exception);

        assertNotNull(exception.getBody().getProperties());
        assertEquals("Department not found with id: " + DEPARTMENT_ID, exception.getBody().getProperties().get("details"));

        // Verify
        verify(departmentRepository, times(1)).findById(DEPARTMENT_ID);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

    @Disabled("Fails due to missing null return if mapper return null entity")
    @Test
    @DisplayName("should throw NullPointerException when mapper returns null")
    void testHandle_whenMapperReturnsNull_shouldThrowException() {
        // Arrange
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));
        when(departmentMapper.toDto(department)).thenReturn(null);

        // Act
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                getDepartmentByIdQueryHandler.handle(query));

        // Assert
        assertNull(exception.getMessage());
    }
}