package cv.igrp.platform.access_management.department.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
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

    private DepartmentDTO dto;
    private Department existingDepartment;
    private Department updatedDepartment;
    private DepartmentDTO updatedDepartmentDTO;

    private UpdateDepartmentCommand updateCommand(DepartmentDTO dto, Integer departmentId){
        return new UpdateDepartmentCommand(dto,departmentId);
    }

    @BeforeEach
    void setUp() {

        dto = new DepartmentDTO();
        dto.setCode("HR");
        dto.setName("Human Resources");
        dto.setDescription("Updated description");

        existingDepartment = new Department();
        updatedDepartment = new Department();

        updatedDepartmentDTO = new DepartmentDTO();
        updatedDepartmentDTO.setCode("HR");
        updatedDepartmentDTO.setName("Human Resources");
        updatedDepartmentDTO.setDescription("Updated description");
    }

    @Test
    @DisplayName("should update department and return 200 OK when department exists")
    void testHandle_whenDepartmentExists_shouldUpdateAndReturnOk() {
        //Give
        Integer departmentId = 1;
        UpdateDepartmentCommand command = updateCommand(dto, departmentId);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(existingDepartment));
        doNothing().when(departmentMapper).updateEntityFromDto(dto, existingDepartment);
        when(departmentRepository.save(existingDepartment)).thenReturn(updatedDepartment);
        when(departmentMapper.toDto(updatedDepartment)).thenReturn(updatedDepartmentDTO);

        // When
        ResponseEntity<DepartmentDTO> response = updateDepartmentCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updatedDepartmentDTO, response.getBody());

        verify(departmentRepository, times(1)).findById(departmentId);
        verify(departmentMapper, times(1)).updateEntityFromDto(dto, existingDepartment);
        verify(departmentRepository, times(1)).save(existingDepartment);
        verify(departmentMapper, times(1)).toDto(updatedDepartment);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);
    }

    @Test
    @DisplayName("should throw EntityNotFoundException when department does not exist")
    void testHandle_whenDepartmentDoesNotExist_shouldThrowEntityNotFoundException(){

        Integer departmentId = -1;
        UpdateDepartmentCommand command = updateCommand(dto, departmentId);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                updateDepartmentCommandHandler.handle(command));

        assertNotNull(exception);
        assertEquals("Department not found with id: " + command.getId(), exception.getMessage());

        verify(departmentRepository, times(1)).findById(departmentId);
        verifyNoMoreInteractions(departmentRepository, departmentMapper);

    }

    @Test
    @DisplayName("should update department when optional fields like description are null")
    void testHandle_whenOptionalFieldsAreNull_shouldStillUpdate(){

        // Given
        Integer departmentId = 1;
        dto.setDescription(null);
        UpdateDepartmentCommand command = new UpdateDepartmentCommand(dto, departmentId);

        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(existingDepartment));
        doNothing().when(departmentMapper).updateEntityFromDto(dto, existingDepartment);
        when(departmentRepository.save(existingDepartment)).thenReturn(updatedDepartment);
        when(departmentMapper.toDto(updatedDepartment)).thenReturn(updatedDepartmentDTO);

        // When
        ResponseEntity<DepartmentDTO> response =  updateDepartmentCommandHandler.handle(command);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(departmentMapper).updateEntityFromDto(dto, existingDepartment);
    }


}