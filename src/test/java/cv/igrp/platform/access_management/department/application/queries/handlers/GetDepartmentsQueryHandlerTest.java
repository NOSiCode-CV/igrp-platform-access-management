package cv.igrp.platform.access_management.department.application.queries.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.department.application.queries.queries.*;

import java.util.List;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class GetDepartmentsQueryHandlerTest {

    @Mock
    DepartmentRepository departmentRepository;

    @Mock
    DepartmentMapper departmentMapper;

    @InjectMocks
    private GetDepartmentsQueryHandler getDepartmentsQueryHandler;

    private Department departmentA, departmentB;
    private DepartmentDTO departmentDTOA, departmentDTOB;

    private GetDepartmentsQuery getDepartmentsQuery(Integer applicationId, String name, String status){
        return new GetDepartmentsQuery(applicationId, null, null, name, status, null);
    }

    @BeforeEach
    void setUp() {
        departmentA = new Department();
        departmentA.setId(1);
        departmentA.setCode("HR");
        departmentA.setName("Human Resources");
        departmentA.setDescription("Handles HR activities");
        departmentA.setStatus(DepartmentStatus.ACTIVE);


        departmentB = new Department();
        departmentB.setId(2);
        departmentB.setCode("FIN");
        departmentB.setName("Finance");
        departmentB.setDescription("Handles financial operations");
        departmentB.setStatus(DepartmentStatus.ACTIVE);

        departmentDTOA = new DepartmentDTO();
        departmentDTOA.setId(1);
        departmentDTOA.setCode("HR");
        departmentDTOA.setName("Human Resources");
        departmentDTOA.setDescription("Handles HR activities");
        departmentDTOA.setStatus(DepartmentStatus.ACTIVE);
        departmentDTOA.setApplication_id(1001);

        departmentDTOB = new DepartmentDTO();
        departmentDTOB.setId(2);
        departmentDTOB.setCode("FIN");
        departmentDTOB.setName("Finance");
        departmentDTOB.setDescription("Handles financial operations");
        departmentDTOB.setStatus(DepartmentStatus.ACTIVE);
        departmentDTOB.setApplication_id(1001);
    }

    @Test
    @DisplayName("should return list of DepartmentDTOs when departments match criteria")
    void testHandle_shouldReturnListOfDepartmentDTOs() {
        // Arrange
        GetDepartmentsQuery query = getDepartmentsQuery(1001, "finance", DepartmentStatus.ACTIVE.name());

        when(departmentRepository.findAll(any(Specification.class))).thenReturn(List.of(departmentA, departmentB));
        when(departmentMapper.toDto(departmentA)).thenReturn(departmentDTOA);
        when(departmentMapper.toDto(departmentB)).thenReturn(departmentDTOB);

        // Act
        ResponseEntity<List<DepartmentDTO>> response = getDepartmentsQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DepartmentDTO> departmentDTOS = response.getBody();
        assertNotNull(departmentDTOS);
        assertEquals(2, departmentDTOS.size());
        assertEquals(departmentDTOA.getCode(), departmentDTOS.get(0).getCode());
        assertEquals(departmentDTOB.getName(), departmentDTOS.get(1).getName());

        // Verify
        verify(departmentRepository, times(1)).findAll(any(Specification.class));
        verify(departmentMapper, times(1)).toDto(departmentA);
        verify(departmentMapper, times(1)).toDto(departmentB);
        verify(departmentMapper).toDto(departmentA);
        verify(departmentMapper).toDto(departmentB);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

    @Test
    @DisplayName("should return empty list when no departments match")
    void testHandle_whenNoDepartmentsMatch_shouldReturnEmptyList() {
        // Arrange
        GetDepartmentsQuery query = getDepartmentsQuery(9999, "nonexistent",DepartmentStatus.ACTIVE.name());
        when(departmentRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // Act
        ResponseEntity<List<DepartmentDTO>> response = getDepartmentsQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DepartmentDTO> departmentDTOS = response.getBody();
        assertNotNull(departmentDTOS);
        assertTrue(departmentDTOS.isEmpty());

        // Verify
        verify(departmentRepository, times(1)).findAll(any(Specification.class));
        verifyNoInteractions(departmentMapper);
        verifyNoMoreInteractions(departmentRepository);
    }

    @Test
    @DisplayName("should return all departments when no filters are provided")
    void testHandle_whenQueryIsEmpty_shouldReturnAllDepartments() {
        // Arrange
        GetDepartmentsQuery query = getDepartmentsQuery(null, null, null);

        when(departmentRepository.findAll(any(Specification.class))).thenReturn(List.of(departmentA, departmentB));
        when(departmentMapper.toDto(departmentA)).thenReturn(departmentDTOA);
        when(departmentMapper.toDto(departmentB)).thenReturn(departmentDTOB);

        // Act
        ResponseEntity<List<DepartmentDTO>> response = getDepartmentsQueryHandler.handle(query);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<DepartmentDTO> departmentDTOS = response.getBody();
        assertNotNull(departmentDTOS);
        assertEquals(2, departmentDTOS.size());
        assertEquals(departmentDTOA.getCode(), departmentDTOS.get(0).getCode());
        assertEquals(departmentDTOB.getName(), departmentDTOS.get(1).getName());

        // Verify
        verify(departmentRepository, times(1)).findAll(any(Specification.class));
        verify(departmentMapper, times(1)).toDto(departmentA);
        verify(departmentMapper, times(1)).toDto(departmentB);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

}