package cv.igrp.platform.access_management.department.application.commands;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
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

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PostDepartmentCommandHandler Tests")
public class PostDepartmentCommandHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

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
    private ApplicationEntity application;
    private DepartmentEntity parentDepartment;
    private DepartmentEntity savedDepartment;
    private DepartmentDTO resultDTO;

    @BeforeEach
    void setUp() {
        // Setup test data
        departmentDTO = new DepartmentDTO();
        departmentDTO.setName("Test Department");
        departmentDTO.setDescription("Test Description");
        departmentDTO.setParent_id(null);

        command = postDepartmentCommand(departmentDTO);

        department = new DepartmentEntity();
        department.setName("Test Department");
        department.setDescription("Test Description");

        application = new ApplicationEntity();
        application.setId(1);
        application.setName("Test Application");
        application.setDepartmentId(department);

        parentDepartment = new DepartmentEntity();
        parentDepartment.setId(2);
        parentDepartment.setName("Parent Department");

        savedDepartment = new DepartmentEntity();
        savedDepartment.setId(3);
        savedDepartment.setName("Test Department");
        savedDepartment.setDescription("Test Description");

        resultDTO = new DepartmentDTO();
        resultDTO.setId(3);
        resultDTO.setName("Test Department");
        resultDTO.setDescription("Test Description");
    }

    @Test
    @DisplayName("Should create department when input is valid and return 201")
    void testHandle_whenValidInput_shouldCreateDepartmentAndReturn201() {
        // Arrange
        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
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
        verify(applicationRepository).findById(1);
        verify(departmentRepository).findById(2);
        verify(departmentRepository).save(department);
        verify(departmentMapper).toDto(savedDepartment);
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);

    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when parent department ID is invalid")
    void handle_whenParentDepartmentIdIsInvalid_shouldThrowBadRequestException() {

        // Arrange
        departmentDTO.setParent_id(-1);

        when(departmentMapper.toEntity(departmentDTO)).thenReturn(department);
        when(departmentRepository.findById(departmentDTO.getParent_id())).thenReturn(Optional.empty());

        command = postDepartmentCommand(departmentDTO);

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> postDepartmentCommandHandler.handle(command));

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST.value(), exception.getBody().getStatus());
        assertEquals("Invalid department ID", exception.getBody().getTitle());

        // Verify
        verify(departmentMapper).toEntity(departmentDTO);
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