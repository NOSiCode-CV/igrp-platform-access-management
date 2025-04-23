package cv.igrp.platform.access_management.menu.application.queries.handlers;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.application.queries.queries.GetMenuByIdQuery;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GetMenuByIdQueryHandlerTest {

    private GetMenuByIdQueryHandler getMenuByIdQueryHandler;

    @Mock
    private MenuEntryRepository menuEntryRepository;

    private MenuEntryMapper menuEntryMapper;

    @BeforeEach
    void setUp() {
        menuEntryMapper = new MenuEntryMapper();
        getMenuByIdQueryHandler = new GetMenuByIdQueryHandler(menuEntryRepository, menuEntryMapper);
    }

    @Test
    void testHandleGetMenuByIdQuery_ReturnsDTO_WhenMenuExists() {
        // Given
        int menuId = 1;
        GetMenuByIdQuery query = new GetMenuByIdQuery(menuId);

        MenuEntry menu = new MenuEntry();
        menu.setId(menuId);
        menu.setName("Test Menu");
        menu.setType(MenuEntryType.MENU_PAGE);
        menu.setPosition((short) 1);
        menu.setIcon("icon-class");
        menu.setStatus(Status.ACTIVE);
        menu.setTarget("_blank");
        menu.setUrl("/test/url");

        when(menuEntryRepository.findById(menuId)).thenReturn(Optional.of(menu));

        // When
        ResponseEntity<MenuEntryDTO> response = getMenuByIdQueryHandler.handle(query);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test Menu", response.getBody().getName());
        assertEquals(1, response.getBody().getId());
    }

    @Test
    void testHandleGetMenuByIdQuery_ThrowsException_WhenMenuNotFound() {
        // Given
        int menuId = 1111;
        GetMenuByIdQuery query = new GetMenuByIdQuery(menuId);

        when(menuEntryRepository.findById(menuId)).thenReturn(Optional.empty());

        // Then
        assertThrows(IgrpResponseStatusException.class, () -> getMenuByIdQueryHandler.handle(query));
    }
}
