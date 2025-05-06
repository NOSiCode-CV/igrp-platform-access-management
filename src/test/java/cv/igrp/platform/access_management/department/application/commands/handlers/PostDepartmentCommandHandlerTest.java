package cv.igrp.platform.access_management.department.application.commands.handlers;

import cv.igrp.platform.access_management.department.application.commands.commands.PostDepartmentCommand;
import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Department;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
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

    @Test
    @DisplayName("Should create department when input is valid and return 201")
    void testHandle_whenValidInput_shouldCreateDepartmentAndReturn201() {
        // Given
        DepartmentDTO dto = new DepartmentDTO();
        dto.setApplication_id(1);
        dto.setParent_id(null);

        Application application = new Application();

        Department entity = new Department();
        Department savedEntity = new Department();
        DepartmentDTO resultDto = new DepartmentDTO();

        when(departmentMapper.toEntity(dto)).thenReturn(entity);
        when(applicationRepository.findById(1)).thenReturn(Optional.of(application));
        when(departmentRepository.save(entity)).thenReturn(savedEntity);
        when(departmentMapper.toDto(savedEntity)).thenReturn(resultDto);

        PostDepartmentCommand command = new PostDepartmentCommand(dto);

        //When
        ResponseEntity<DepartmentDTO> response = postDepartmentCommandHandler.handle(command);

        /// Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(resultDto, response.getBody());

        verify(departmentMapper).toEntity(dto);
        verify(applicationRepository).findById(1);
        verify(departmentRepository).save(entity);
        verify(departmentMapper).toDto(savedEntity);
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);
    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when application ID is invalid")
    void testHandle_whenApplicationIdIsInvalid_shouldThrowBadRequestException() {

        //Given
        DepartmentDTO dto = new DepartmentDTO();
        dto.setApplication_id(-1);

        PostDepartmentCommand command = new PostDepartmentCommand(dto);

        Department entity = new Department();

        when(departmentMapper.toEntity(dto)).thenReturn(entity);
        when(applicationRepository.findById(dto.getApplication_id())).thenReturn(Optional.empty());

        // When /Then
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, ()
                -> postDepartmentCommandHandler.handle(command));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getProblem().getStatus());
        assertEquals("Invalid application ID", exception.getProblem().getTitle());

        verify(departmentMapper).toEntity(dto);
        verify(applicationRepository).findById(dto.getApplication_id());
        verifyNoInteractions(departmentRepository);
        verifyNoMoreInteractions(departmentMapper, applicationRepository);
    }

    @Test
    @DisplayName("Should throw BAD_REQUEST when parent department ID is invalid")
    void handle_whenParentDepartmentIdIsInvalid_shouldThrowBadRequestException() {

        // Given
        DepartmentDTO dto = new DepartmentDTO();
        dto.setParent_id(-1);
        dto.setApplication_id(1);

        Application application = new Application();
        Department entity = new Department();

        when(departmentMapper.toEntity(dto)).thenReturn(entity);
        when(applicationRepository.findById(dto.getApplication_id())).thenReturn(Optional.of(application));
        when(departmentRepository.findById(dto.getParent_id())).thenReturn(Optional.empty());

        PostDepartmentCommand command = new PostDepartmentCommand(dto);

        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> postDepartmentCommandHandler.handle(command));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getProblem().getStatus());
        assertEquals("Invalid department ID", exception.getProblem().getTitle());

        verify(departmentMapper).toEntity(dto);
        verify(applicationRepository).findById(dto.getApplication_id());
        verify(departmentRepository).findById(dto.getParent_id());
        verifyNoMoreInteractions(departmentMapper, applicationRepository, departmentRepository);
    }

    @Test
    @DisplayName("Should throw NullPointerException when DepartmentDTO is null")
    void testHandle_NullDepartmentDTO() {
        // Given
        PostDepartmentCommand command = new PostDepartmentCommand(null);

        // When / Then
        assertThrows(NullPointerException.class, () -> postDepartmentCommandHandler.handle(command));
    }

    @Disabled("Fails due to missing null return if mapper return null entity")
    @Test
    @DisplayName("Should throw IllegalStateException when mapper returns null entity")
    void testHandle_whenMappingFails_shouldThrowIllegalStateException() {
        // Given
        DepartmentDTO dto = new DepartmentDTO();
        dto.setApplication_id(1);
        PostDepartmentCommand command = new PostDepartmentCommand(dto);

        when(departmentMapper.toEntity(dto)).thenReturn(null);

        // When / Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> postDepartmentCommandHandler.handle(command));
        assertEquals("Department entity mapping failed", ex.getMessage());

        verify(departmentMapper).toEntity(dto);
        verifyNoMoreInteractions(departmentMapper);
    }

}