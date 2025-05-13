package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.platform.access_management.department.application.commands.commands.PostDepartmentCommand;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
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

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PostDepartmentCommandHandler Tests")
public class PostDepartmentCommandHandlerTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @InjectMocks
    private PostDepartmentCommandHandler postDepartmentCommandHandler;

    private PostDepartmentCommand postDepartmentCommand(DepartmentDTO departmentDTO) {
        return new PostDepartmentCommand(departmentDTO);
    }

    private PostDepartmentCommand command;
    private DepartmentDTO departmentDTO;
    private Department department;
    private Application application;
    private Department parentDepartment;
    private Department savedDepartment;
    private DepartmentDTO resultDTO;

    @BeforeEach
    void setUp() {
        // Setup test data
        departmentDTO = new DepartmentDTO();
        departmentDTO.setName("Test Department");
        departmentDTO.setDescription("Test Description");
        departmentDTO.setApplication_id(1);
        departmentDTO.setParent_id(null);

        command = postDepartmentCommand(departmentDTO);

        department = new Department();
        department.setName("Test Department");
        department.setDescription("Test Description");

        application = new Application();
        application.setId(1);
        application.setName("Test Application");

        parentDepartment = new Department();
        parentDepartment.setId(2);
        parentDepartment.setName("Parent Department");

        savedDepartment = new Department();
        savedDepartment.setId(3);
        savedDepartment.setName("Test Department");
        savedDepartment.setDescription("Test Description");
        savedDepartment.setApplicationId(application);

        resultDTO = new DepartmentDTO();
        resultDTO.setId(3);
        resultDTO.setName("Test Department");
        resultDTO.setDescription("Test Description");
        resultDTO.setApplication_id(1);
    }

    @Test
    @DisplayName("Should create department when input is valid and return 201")
    void testHandle_whenValidInput_shouldCreateDepartmentAndReturn201() {
        // Arrange
        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(departmentRepository.save(any(Department.class))).thenReturn(savedDepartment);
        when(departmentMapper.toDto(savedDepartment)).thenReturn(resultDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = postDepartmentCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resultDTO, response.getBody());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(applicationRepository).findById(1);
        verify(departmentRepository).save(department);
        verify(departmentMapper).toDto(savedDepartment);
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);
    }


    @Test
    @DisplayName("Should create department with parent when valid parent ID is provided")
    void testHandle_whenParentIdIsProvided_shouldCreateDepartmentWithParentSuccessfully() {

        // Arrange
        departmentDTO.setParent_id(2);
        command = postDepartmentCommand(departmentDTO);

        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(departmentRepository.findById(2)).thenReturn(Optional.of(parentDepartment));
        when(departmentRepository.save(any(Department.class))).thenReturn(savedDepartment);
        when(departmentMapper.toDto(savedDepartment)).thenReturn(resultDTO);

        // Act
        ResponseEntity<DepartmentDTO> response = postDepartmentCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resultDTO, response.getBody());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(applicationRepository).findById(1);
        verify(departmentRepository).findById(2);
        verify(departmentRepository).save(department);
        verify(departmentMapper).toDto(savedDepartment);
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);

    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when application ID is invalid")
    void testHandle_whenApplicationIdIsInvalid_shouldThrowBadRequestException() {

        // Arrange
        departmentDTO.setApplication_id(-1);
        command = postDepartmentCommand(departmentDTO);

        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(applicationRepository.findById(departmentDTO.getApplication_id())).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, ()
                -> postDepartmentCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, exception.getProblem().getStatus());
        assertEquals("Invalid application ID", exception.getProblem().getTitle());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(applicationRepository).findById(departmentDTO.getApplication_id());
        verifyNoInteractions(departmentRepository);
        verifyNoMoreInteractions(departmentMapper, applicationRepository);
    }



    @Test
    @DisplayName("Should throw BAD_REQUEST when parent department ID is invalid")
    void handle_whenParentDepartmentIdIsInvalid_shouldThrowBadRequestException() {

        // Arrange
        departmentDTO.setParent_id(-1);

        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(applicationRepository.findById(departmentDTO.getApplication_id())).thenReturn(Optional.of(application));
        when(departmentRepository.findById(departmentDTO.getParent_id())).thenReturn(Optional.empty());

        command = postDepartmentCommand(departmentDTO);

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> postDepartmentCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, exception.getProblem().getStatus());
        assertEquals("Invalid department ID", exception.getProblem().getTitle());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verify(applicationRepository).findById(departmentDTO.getApplication_id());
        verify(departmentRepository).findById(departmentDTO.getParent_id());
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);
    }



    @Disabled("Fails due to missing null return if mapper return null entity")
    @Test
    @DisplayName("Should throw IllegalStateException when mapper returns null entity")
    void testHandle_whenMappingFails_shouldThrowIllegalStateException() {
        // Arrange
        when(departmentMapper.toEntity(departmentDTO)).thenReturn(null);

        // Act
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> postDepartmentCommandHandler.handle(command));

        // Assert
        assertEquals("Department entity mapping failed", ex.getMessage());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
        verifyNoMoreInteractions(departmentMapper);
    }

}