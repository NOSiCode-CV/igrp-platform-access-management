package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenusQuery;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.MenuEntry;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.MenuEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
public class GetMenusQueryHandlerTest {

    private GetMenusQueryHandler getMenusQueryHandler;

    @Mock
    private MenuEntryRepository menuEntryRepository;

    private MenuEntryMapper menuEntryMapper;

    @BeforeEach
    void setUp() {
        menuEntryMapper = new MenuEntryMapper();
        getMenusQueryHandler = new GetMenusQueryHandler(menuEntryRepository, menuEntryMapper);
    }

    @Test
    void testHandleGetMenusQuery() {
        // Given
        GetMenusQuery query = new GetMenusQuery(1, "dashboard","MENU_PAGE");

        MenuEntry menu = new MenuEntry();
        menu.setId(10);
        menu.setName("Dashboard");
        menu.setType(MenuEntryType.MENU_PAGE);
        menu.setPosition((short) 1);
        menu.setIcon("icon-dashboard");
        menu.setStatus(Status.ACTIVE);
        menu.setTarget("_self");
        menu.setUrl("/dashboard");

        Application app = new Application();
        app.setId(1);
        menu.setApplicationId(app);

        List<MenuEntry> menuList = List.of(menu);

        Mockito.when(menuEntryRepository.findAll(Mockito.any(Specification.class)))
                .thenReturn(menuList);

        // When
        ResponseEntity<List<MenuEntryDTO>> response = getMenusQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());

        MenuEntryDTO menuResult = response.getBody().get(0);
        assertEquals(menu.getId(), menuResult.getId());
        assertEquals(menu.getName(), menuResult.getName());
        assertEquals(menu.getType(), menuResult.getType());
        assertEquals(menu.getApplicationId().getId(), menuResult.getApplicationId());
    }
}
