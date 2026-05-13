package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GetUserApplicationMenusQueryHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @InjectMocks
    private GetUserApplicationMenusQueryHandler handler;

    private IGRPUserEntity user;
    private ApplicationEntity app;
    private MenuEntryDTO menuEntryDTO1;
    private MenuEntryDTO menuEntryDTO2;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000001");
        user.setEmail("user@igrp.cv");

        app = new ApplicationEntity();
        app.setId(1);
        app.setCode("APP1");

        menuEntryDTO1 = new MenuEntryDTO();
        menuEntryDTO1.setId(1);
        menuEntryDTO1.setCode("MENU_A");
        menuEntryDTO1.setName("Menu A");

        menuEntryDTO2 = new MenuEntryDTO();
        menuEntryDTO2.setId(2);
        menuEntryDTO2.setCode("MENU_B");
        menuEntryDTO2.setName("Menu B");

    }

    // ------------------------------------------------------
    // 1. SUCCESS: returns all menus
    // ------------------------------------------------------
    @Test
    void handle_success_returnsMenus() {

        GetUserApplicationMenusQuery query =
                new GetUserApplicationMenusQuery(null, "00000000-0000-0000-0000-000000000001", "APP1");

        when(userRepository.findById("00000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        MenuEntryEntity menu1 = new MenuEntryEntity();
        menu1.setCode("MENU_A");
        MenuEntryEntity menu2 = new MenuEntryEntity();
        menu2.setCode("MENU_B");

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(any(), any(), any()))
                .thenReturn(List.of(menu1, menu2));

        when(menuEntryMapper.toDTO(menu1)).thenReturn(menuEntryDTO1);
        when(menuEntryMapper.toDTO(menu2)).thenReturn(menuEntryDTO2);

        ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        verify(menuEntryRepository).findActiveByApplicationIdAndUserIdFiltered(user.getId(), app.getId(), null);
    }

    // ------------------------------------------------------
    // 2. SUCCESS: filter by menu code
    // ------------------------------------------------------
    @Test
    void handle_success_filtersByMenuCode() {

        GetUserApplicationMenusQuery query =
                new GetUserApplicationMenusQuery("A", "00000000-0000-0000-0000-000000000001", "APP1");

        when(userRepository.findById("00000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        MenuEntryEntity menu1 = new MenuEntryEntity();
        menu1.setCode("MENU_A");
        MenuEntryEntity menu2 = new MenuEntryEntity();
        menu2.setCode("MENU_B");

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(any(), any(), any()))
                .thenReturn(List.of(menu1));

        when(menuEntryMapper.toDTO(menu1)).thenReturn(menuEntryDTO1);

        ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("MENU_A", response.getBody().get(0).getCode());
    }

    // ------------------------------------------------------
    // 3. ERROR: user not found → throws unauthorized
    // ------------------------------------------------------
    @Test
    void handle_userNotFound_throwsUnauthorized() {

        when(userRepository.findById("00000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.empty());

        GetUserApplicationMenusQuery query =
                new GetUserApplicationMenusQuery(null, "00000000-0000-0000-0000-000000000001", "APP1");

        assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(query));

        verify(menuEntryRepository, never()).findActiveByApplicationIdAndUserIdFiltered(any(), any(), isNull());
    }

    // ------------------------------------------------------
    // 4. SUCCESS: no menus found → return empty list
    // ------------------------------------------------------
    @Test
    void handle_success_returnsEmptyList() {

        when(userRepository.findById("00000000-0000-0000-0000-000000000001"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(any(), any(), any()))
                .thenReturn(List.of());

        GetUserApplicationMenusQuery query =
                new GetUserApplicationMenusQuery(null, "00000000-0000-0000-0000-000000000001", "APP1");

        ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}