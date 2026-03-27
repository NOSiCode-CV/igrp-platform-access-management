package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
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

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PostDepartmentCommandHandler Tests")
public class PostDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private PostDepartmentCommandHandler postDepartmentCommandHandler;

    private PostDepartmentCommand postDepartmentCommand(DepartmentDTO departmentDTO) {
        return new PostDepartmentCommand(departmentDTO);
    }

    private PostDepartmentCommand command;
    private DepartmentDTO departmentDTO;
    private DepartmentEntity department;
    private DepartmentEntity parentDepartment;
    private DepartmentEntity savedDepartment;
    private DepartmentDTO resultDTO;

    @BeforeEach
    void setUp() {
        // Setup test data
        departmentDTO = new DepartmentDTO();
        departmentDTO.setName("Test Department");
        departmentDTO.setCode("DEPT_TEST");
        departmentDTO.setDescription("Test Description");
        departmentDTO.setParentCode(null);

        command = postDepartmentCommand(departmentDTO);

        department = new DepartmentEntity();
        department.setCode("DEPT_TEST");
        department.setName("Test Department");
        department.setDescription("Test Description");

        parentDepartment = new DepartmentEntity();
        parentDepartment.setId(2);
        parentDepartment.setCode("DEPT_RH");
        parentDepartment.setName("Parent Department");

        savedDepartment = new DepartmentEntity();
        savedDepartment.setId(3);
        savedDepartment.setCode("DEPT_TEST");
        savedDepartment.setName("Test Department");
        savedDepartment.setDescription("Test Description");

        resultDTO = new DepartmentDTO();
        resultDTO.setId(3);
        resultDTO.setCode("DEPT_TEST");
        resultDTO.setName("Test Department");
        resultDTO.setDescription("Test Description");
    }

    @Test
    @DisplayName("Should create department when input is valid and return 201")
    void testHandle_whenValidInput_shouldCreateDepartmentAndReturn201() {
        // Arrange
        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(departmentRepository.findByCodeAndStatusNot("DEPT_TEST", DepartmentStatus.DELETED)).thenReturn(Optional.empty());
        when(departmentRepository.save(any(DepartmentEntity.class))).thenReturn(savedDepartment);
        when(departmentMapper.toDto(savedDepartment)).thenReturn(resultDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = postDepartmentCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resultDTO, response.getBody());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(departmentRepository).save(department);
        verify(departmentRepository).findByCodeAndStatusNot("DEPT_TEST", DepartmentStatus.DELETED);
        verify(departmentMapper).toDto(savedDepartment);
        verifyNoMoreInteractions(departmentMapper, departmentRepository);
    }


    @Test
    @DisplayName("Should create department with parent when valid parent ID is provided")
    void testHandle_whenParentIdIsProvided_shouldCreateDepartmentWithParentSuccessfully() {

        // Arrange
        departmentDTO.setParentCode("DEPT_RH");
        command = postDepartmentCommand(departmentDTO);

        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(departmentRepository.findByCodeAndStatusNot("DEPT_TEST", DepartmentStatus.DELETED)).thenReturn(Optional.empty());
        when(departmentRepository.findByCodeAndStatusNot("DEPT_RH", DepartmentStatus.DELETED)).thenReturn(Optional.of(parentDepartment));
        when(departmentRepository.save(any(DepartmentEntity.class))).thenReturn(savedDepartment);

        when(departmentMapper.toDto(savedDepartment)).thenReturn(resultDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = postDepartmentCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resultDTO, response.getBody());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(departmentRepository).findByCodeAndStatusNot("DEPT_TEST", DepartmentStatus.DELETED);
        verify(departmentRepository).findByCodeAndStatusNot("DEPT_RH", DepartmentStatus.DELETED);
        verify(departmentRepository).save(department);
        verify(departmentMapper).toDto(savedDepartment);
        verifyNoMoreInteractions(departmentMapper, departmentRepository);

    }

    @Test
    @DisplayName("Should throw NullPointerException when mapper returns null entity")
    void testHandle_whenMappingFails_shouldThrowIllegalStateException() {
        // Arrange
        when(departmentMapper.toEntity(departmentDTO)).thenReturn(null);

        // Act
        NullPointerException ex = assertThrows(NullPointerException.class, () -> postDepartmentCommandHandler.handle(command));

        // Assert
        assertTrue(ex.getMessage().contains("\"department\" is null"));

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verifyNoMoreInteractions(departmentMapper);
    }

}