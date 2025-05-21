package cv.igrp.platform.access_management.department.application.commands.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.department.application.commands.commands.*;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class UpdateDepartmentCommandHandlerTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private UpdateDepartmentCommandHandler updateDepartmentCommandHandler;

    private UpdateDepartmentCommand command;
    private DepartmentDTO departmentDTO;
    private Department existingDepartment;
    private Department updatedDepartment;
    private DepartmentDTO updatedDepartmentDTO;
    private final Integer DEPARTMENT_ID = 1;

    private UpdateDepartmentCommand updateCommand(DepartmentDTO dto){
        return new UpdateDepartmentCommand(dto,DEPARTMENT_ID);
    }

    @BeforeEach
    void setUp() {

        departmentDTO = new DepartmentDTO();
        departmentDTO.setCode("HR");
        departmentDTO.setName("Human Resources");
        departmentDTO.setDescription("Updated description");

        command = updateCommand(departmentDTO);

        existingDepartment = new Department();
        existingDepartment.setId(DEPARTMENT_ID);
        existingDepartment.setName("Original Human Resources");
        existingDepartment.setDescription("Original description");

        updatedDepartment = new Department();
        updatedDepartment.setId(DEPARTMENT_ID);
        updatedDepartment.setName("Updated Human Resources");
        updatedDepartment.setDescription("Updated description");

        updatedDepartmentDTO = new DepartmentDTO();
        updatedDepartment.setId(DEPARTMENT_ID);
        updatedDepartmentDTO.setCode("HR");
        updatedDepartmentDTO.setName("Updated Human Resources");
        updatedDepartmentDTO.setDescription("Updated description");
    }

    @Test
    @DisplayName("should update department and return 200 OK when department exists")
    void testHandle_whenDepartmentExists_shouldUpdateAndReturnOk() {
        // Arrange
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(existingDepartment));
        doNothing().when(departmentMapper).updateEntityFromDto(departmentDTO, existingDepartment);
        when(departmentRepository.save(existingDepartment)).thenReturn(updatedDepartment);
        when(departmentMapper.toDto(updatedDepartment)).thenReturn(updatedDepartmentDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = updateDepartmentCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updatedDepartmentDTO, response.getBody());

        // Verify
        verify(departmentRepository, times(1)).findById(DEPARTMENT_ID);
        verify(departmentMapper, times(1)).updateEntityFromDto(departmentDTO, existingDepartment);
        verify(departmentRepository, times(1)).save(existingDepartment);
        verify(departmentMapper, times(1)).toDto(updatedDepartment);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when department does not exist")
    void testHandle_whenDepartmentDoesNotExist_shouldThrowEntityNotFoundException(){
        // Arrange
        command = updateCommand(departmentDTO);
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                updateDepartmentCommandHandler.handle(command));

        // Assert
        assertNotNull(exception);
        assertEquals("Department not found with id: " + command.getId(), exception.getProblem().getDetails());

        // Verify
        verify(departmentRepository, times(1)).findById(DEPARTMENT_ID);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);

    }

    @Test
    @DisplayName("should update department when optional fields like description are null")
    void testHandle_whenOptionalFieldsAreNull_shouldStillUpdate(){
        // Arrange
        departmentDTO.setDescription(null);
        command = updateCommand(departmentDTO);

        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(existingDepartment));
        doNothing().when(departmentMapper).updateEntityFromDto(departmentDTO, existingDepartment);
        when(departmentRepository.save(existingDepartment)).thenReturn(updatedDepartment);
        when(departmentMapper.toDto(updatedDepartment)).thenReturn(updatedDepartmentDTO);

        // Act
        ResponseEntity<DepartmentDTO> response =  updateDepartmentCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify
        verify(departmentMapper).updateEntityFromDto(departmentDTO, existingDepartment);
    }
}