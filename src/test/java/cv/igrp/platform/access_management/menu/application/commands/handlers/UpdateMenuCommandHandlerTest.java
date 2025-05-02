package cv.igrp.platform.access_management.menu.application.commands.handlers;

import cv.igrp.platform.access_management.menu.application.commands.commands.UpdateMenuCommand;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdateMenuCommandHandlerTest {

    private UpdateMenuCommandHandler updateMenuCommandHandler;

    @Mock
    private MenuEntryRepository menuEntryRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ResourceRepository resourceRepository;

    private MenuEntryMapper menuEntryMapper;

    @BeforeEach
    void setUp() {
        this.menuEntryMapper = new MenuEntryMapper();
        updateMenuCommandHandler = new UpdateMenuCommandHandler(
                menuEntryRepository,
                menuEntryMapper,
                applicationRepository,
                resourceRepository
        );
    }

    @Test
    void testHandle() {
        // Given
        Integer menuId = 1;
        Integer parentId = 2;
        Integer appId = 3;
        Integer resId = 4;

        MenuEntryDTO dto = new MenuEntryDTO();
        dto.setId(menuId);
        dto.setName("Updated Menu");
        dto.setType(MenuEntryType.MENU_PAGE);
        dto.setPosition((short) 1);
        dto.setIcon("icon.png");
        dto.setStatus(Status.ACTIVE);
        dto.setTarget("_blank");
        dto.setUrl("/updated-url");
        dto.setParentId(parentId);
        dto.setApplicationId(appId);
        dto.setResourceId(resId);

        UpdateMenuCommand command = new UpdateMenuCommand();
        command.setId(menuId);
        command.setMenuentrydto(dto);

        MenuEntry existingMenu = new MenuEntry();
        existingMenu.setId(menuId);

        MenuEntry parentMenu = new MenuEntry();
        parentMenu.setId(parentId);

        Application app = new Application();
        app.setId(appId);

        Resource res = new Resource();
        res.setId(resId);

        MenuEntry savedMenu = new MenuEntry();
        savedMenu.setId(menuId);
        savedMenu.setName("Updated Menu");
        savedMenu.setType(MenuEntryType.MENU_PAGE);
        savedMenu.setPosition((short) 1);
        savedMenu.setIcon("icon.png");
        savedMenu.setStatus(Status.ACTIVE);
        savedMenu.setTarget("_blank");
        savedMenu.setUrl("/updated-url");
        savedMenu.setParentId(parentMenu);
        savedMenu.setApplicationId(app);
        savedMenu.setResourceId(res);

        when(menuEntryRepository.findById(menuId)).thenReturn(Optional.of(existingMenu));
        when(menuEntryRepository.findById(parentId)).thenReturn(Optional.of(parentMenu));
        when(applicationRepository.findById(appId)).thenReturn(Optional.of(app));
        when(resourceRepository.findById(resId)).thenReturn(Optional.of(res));
        when(menuEntryRepository.save(any(MenuEntry.class))).thenReturn(savedMenu);

        // When
        ResponseEntity<MenuEntryDTO> response = updateMenuCommandHandler.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Updated Menu", response.getBody().getName());
        assertEquals(parentId, response.getBody().getParentId());
        assertEquals(appId, response.getBody().getApplicationId());
        assertEquals(resId, response.getBody().getResourceId());

        verify(menuEntryRepository).save(any(MenuEntry.class));
    }
}
