package cv.igrp.platform.access_management.department.application.commands.handlers;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.shared.infrastructure.persistence.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
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

    @InjectMocks
    private DeleteDepartmentCommandHandler deleteDepartmentCommandHandler;

    @Mock
    private DepartmentRepository departmentRepository;

    private DeleteDepartmentCommand createCommand(Integer id) {
        return new DeleteDepartmentCommand(id);
    }

    @Test
    @DisplayName("Should delete department and return 204 when department exists")
    void testHandle_whenDepartmentExists_shouldDeleteAndReturnNoContent() {
        //Given

        Integer departmentId = 1;
        DeleteDepartmentCommand command = createCommand(departmentId);

        when(departmentRepository.existsById(departmentId)).thenReturn(true);

        //When
        ResponseEntity<Void> response = deleteDepartmentCommandHandler.handle(command);

        //Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode().value());
        verify(departmentRepository).existsById(departmentId);
        verify(departmentRepository).deleteById(departmentId);
        verifyNoMoreInteractions(departmentRepository);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when department does not exist")
    void testHandle_whenDepartmentDoesNotExist_shouldThrowException() {

        //Given
        Integer departmentId = -1;
        DeleteDepartmentCommand command = createCommand(departmentId);

        when(departmentRepository.existsById(departmentId)).thenReturn(false);

        //When Then
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () ->
                deleteDepartmentCommandHandler.handle(command));

        assertEquals("Department not found with id: 99", exception.getMessage());

        verify(departmentRepository).existsById(departmentId);
        verify(departmentRepository, never()).deleteById(departmentId);
        verifyNoMoreInteractions(departmentRepository);
    }

}