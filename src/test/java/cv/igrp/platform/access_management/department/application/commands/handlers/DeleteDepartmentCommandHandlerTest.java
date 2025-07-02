package cv.igrp.platform.access_management.department.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteDepartmentCommandHandler Tests")
public class DeleteDepartmentCommandHandlerTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DeleteDepartmentCommandHandler deleteDepartmentCommandHandler;

    private DeleteDepartmentCommand command;
    private final Integer DEPARTMENT_ID = 1;

    private DeleteDepartmentCommand createCommand(Integer id) {
        return new DeleteDepartmentCommand(id);
    }

    @BeforeEach
    void setUp() {
        command = createCommand(DEPARTMENT_ID);
    }

    @Test
    @DisplayName("Should delete department and return 204 when department exists")
    void testHandle_whenDepartmentExists_shouldDeleteAndReturnNoContent() {
        // Arrange
        when(departmentRepository.existsById(DEPARTMENT_ID)).thenReturn(true);

        // Act
        ResponseEntity<Void> response = deleteDepartmentCommandHandler.handle(command);

        // Assert
        assertNull(response.getBody());
        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode().value());

        // Verify
        verify(departmentRepository).existsById(DEPARTMENT_ID);
        verify(departmentRepository).deleteById(DEPARTMENT_ID);
        verifyNoMoreInteractions(departmentRepository);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when department does not exist")
    void testHandle_whenDepartmentDoesNotExist_shouldThrowException() {
        // Arrange

        when(departmentRepository.existsById(DEPARTMENT_ID)).thenReturn(false);

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () ->
                deleteDepartmentCommandHandler.handle(command));

        // Assert
        assertEquals("Department not found with id: " + DEPARTMENT_ID, exception.getBody().getDetail());

        // Verify
        verify(departmentRepository).existsById(DEPARTMENT_ID);
        verify(departmentRepository, never()).deleteById(DEPARTMENT_ID);
        verifyNoMoreInteractions(departmentRepository);
    }

}