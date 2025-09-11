package cv.igrp.platform.access_management.menu.application.commands;

import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteMenuCommandHandler Tests")
public class DeleteMenuCommandHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @InjectMocks
    private DeleteMenuCommandHandler deleteMenuCommandHandler;

    private DeleteMenuCommand deleteMenuCommand(String code){
        return new DeleteMenuCommand(code);
    }

    private MenuEntryEntity menuEntry;
    private DeleteMenuCommand command;

    @BeforeEach
    void setUp() {
        menuEntry = new MenuEntryEntity();
        menuEntry.setId(1);
        menuEntry.setStatus(Status.ACTIVE);
    }

    @Test
    @DisplayName("should soft delete menu and return 204 No Content")
    void testHandle_whenMenuExists_shouldSoftDeleteAndReturnNoContent() {
        // Arrange
        command = deleteMenuCommand("MENU1");
        when(menuEntryRepository.findByCodeAndStatusNot("MENU1", Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // Act
        ResponseEntity<String> response = deleteMenuCommandHandler.handle(command);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(Status.DELETED, menuEntry.getStatus());

        // Verify
        verify(menuEntryRepository, times(1)).findByCodeAndStatusNot("MENU1", Status.DELETED);
        verify(menuEntryRepository, times(1)).save(menuEntry);
        verifyNoMoreInteractions(menuEntryRepository);

    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException when menu not found")
    void testHandle_whenMenuNotFound_shouldThrowException() {
        // Arrange
        command = deleteMenuCommand("MENU1");
        when(menuEntryRepository.findByCodeAndStatusNot("MENU1", Status.DELETED)).thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class, () -> deleteMenuCommandHandler.handle(command));

        ProblemDetail problem = exception.getBody();
        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertNotNull(problem.getProperties());
        assertTrue(problem.getProperties().getOrDefault("details", "").toString().contains("Menu not found with code: MENU1"));

        verify(menuEntryRepository, times(1)).findByCodeAndStatusNot("MENU1", Status.DELETED);
        verifyNoMoreInteractions(menuEntryRepository);
    }

    @Test
    @DisplayName("should throw IgrpResponseStatusException if command ID is null")
    void testHandle_whenCommandCodeIsNull_shouldThrowCustomException() {
        // Arrange
        command = deleteMenuCommand(null);
        //noinspection DataFlowIssue
        when(menuEntryRepository.findByCodeAndStatusNot(null, Status.DELETED)).thenReturn(Optional.empty());

        // Act & Assert
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class, () -> deleteMenuCommandHandler.handle(command));

        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    @DisplayName("should still return 204 if menu is already marked as DELETED")
    void testHandle_whenMenuAlreadyDeleted_shouldStillReturnNoContent() {
        // Arrange
        menuEntry.setStatus(Status.DELETED);
        command = deleteMenuCommand("MENU1");

        when(menuEntryRepository.findByCodeAndStatusNot("MENU1", Status.DELETED)).thenReturn(Optional.of(menuEntry));
        when(menuEntryRepository.save(menuEntry)).thenReturn(menuEntry);

        // Act
        ResponseEntity<String> response = deleteMenuCommandHandler.handle(command);

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(menuEntryRepository).save(menuEntry);
    }
}
