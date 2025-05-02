package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.platform.access_management.menu.application.commands.commands.DeleteMenuCommand;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeleteMenuCommandHandlerTest {

    @InjectMocks
    private DeleteMenuCommandHandler deleteMenuCommandHandler;

    @Mock
    private MenuEntryRepository menuEntryRepository; // Mock the repository

    @BeforeEach
    void setUp() {
        // ...
    }

    @Test
    void testHandle_whenMenuFound() {
        // Given
        Integer menuId = 1;
        DeleteMenuCommand command = new DeleteMenuCommand(menuId);

        MenuEntry mockMenu = new MenuEntry();
        mockMenu.setId(menuId);
        mockMenu.setStatus(Status.ACTIVE);

        when(menuEntryRepository.findById(menuId)).thenReturn(Optional.of(mockMenu));

        // When
        ResponseEntity<String> response = deleteMenuCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        verify(menuEntryRepository, times(1)).save(mockMenu);
        assertEquals(Status.DELETED, mockMenu.getStatus());
    }

    @Test
    void testHandle_whenMenuNotFound() {
        // Given
        Integer menuId = 999;
        DeleteMenuCommand command = new DeleteMenuCommand(menuId);

        // When
        when(menuEntryRepository.findById(menuId)).thenReturn(Optional.empty());

        // Then
        IgrpResponseStatusException thrown = assertThrows(IgrpResponseStatusException.class, () -> {
            deleteMenuCommandHandler.handle(command);
        });

        assertEquals(HttpStatus.NOT_FOUND, thrown.getProblem().getStatus());
        assertTrue(thrown.getProblem().getTitle().contains("Menu not found"));
    }
}
