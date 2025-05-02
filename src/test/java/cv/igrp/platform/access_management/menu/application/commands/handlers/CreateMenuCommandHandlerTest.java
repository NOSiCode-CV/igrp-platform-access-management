package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.platform.access_management.menu.application.commands.commands.CreateMenuCommand;
import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.domain.models.Resource;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ApplicationRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.ResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CreateMenuCommandHandlerTest {

    private CreateMenuCommandHandler createMenuCommandHandler;

    @Mock
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ResourceRepository resourceRepository;

    private MenuEntryMapper menuEntryMapper;

    @BeforeEach
    void setUp() {
        menuEntryMapper = new MenuEntryMapper();
        createMenuCommandHandler = new CreateMenuCommandHandler(menuEntryRepository, menuEntryMapper, applicationRepository, resourceRepository);
    }

    @Test
    void testHandle() {
        // Given
        MenuEntryDTO menuEntryDTO = new MenuEntryDTO();
        menuEntryDTO.setName("Menu Item");
        menuEntryDTO.setType(MenuEntryType.MENU_PAGE);
        menuEntryDTO.setPosition((short) 1);
        menuEntryDTO.setStatus(Status.ACTIVE);
        menuEntryDTO.setUrl("http://example.com");

        CreateMenuCommand command = new CreateMenuCommand(menuEntryDTO);

        Application application = new Application();
        application.setId(1);
        Resource resource = new Resource();
        resource.setId(1);
        menuEntryDTO.setApplicationId(application.getId());
        menuEntryDTO.setResourceId(resource.getId());

        when(applicationRepository.getReferenceById(anyInt())).thenReturn(application);
        when(resourceRepository.getReferenceById(anyInt())).thenReturn(resource);
        when(menuEntryRepository.save(any(MenuEntry.class))).thenAnswer(invocation -> {
            MenuEntry saved = invocation.getArgument(0);
            saved.setId(100);
            return saved;
        });

        // When
        ResponseEntity<MenuEntryDTO> response = createMenuCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Menu Item", response.getBody().getName());
        assertEquals(MenuEntryType.MENU_PAGE, response.getBody().getType());
        assertEquals((short) 1, response.getBody().getPosition());
        verify(menuEntryRepository, times(1)).save(any(MenuEntry.class));
    }
}
