package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.MenuEntryEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
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
class GetCurrentUserApplicationMenusQueryHandlerTest {

    @Mock
    private MenuEntryEntityRepository menuEntryRepository;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private MenuEntryMapper menuEntryMapper;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @InjectMocks
    private GetCurrentUserApplicationMenusQueryHandler handler;

    private IGRPUserEntity user;
    private ApplicationEntity app;
    private MenuEntryDTO menuEntryDTO1;
    private MenuEntryDTO menuEntryDTO2;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(1);
        user.setEmail("user@igrp.cv");
        user.setExternalId("ext-user-1");

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

        GetCurrentUserApplicationMenusQuery query =
                new GetCurrentUserApplicationMenusQuery(null, "APP1");

        when(authenticationHelper.getSub()).thenReturn("ext-user-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("ext-user-1"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        MenuEntryEntity menu1 = new MenuEntryEntity();
        menu1.setCode("MENU_A");
        menu1.setStatus(Status.ACTIVE);
        MenuEntryEntity menu2 = new MenuEntryEntity();
        menu2.setCode("MENU_B");
        menu2.setStatus(Status.ACTIVE);

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(Integer.valueOf(user.getId()), app.getId(), null))
                .thenReturn(List.of(menu1, menu2));

        when(menuEntryMapper.toDTO(menu1)).thenReturn(menuEntryDTO1);
        when(menuEntryMapper.toDTO(menu2)).thenReturn(menuEntryDTO2);

        ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertEquals(2, response.getBody().size());
        verify(menuEntryRepository).findActiveByApplicationIdAndUserIdFiltered(Integer.valueOf(user.getId()), app.getId(), null);
    }

    // ------------------------------------------------------
    // 2. SUCCESS: filter by menu code
    // ------------------------------------------------------
    @Test
    void handle_success_filtersByMenuCode() {

        GetCurrentUserApplicationMenusQuery query =
                new GetCurrentUserApplicationMenusQuery("A", "APP1");

        when(authenticationHelper.getSub()).thenReturn("ext-user-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("ext-user-1"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        MenuEntryEntity menu1 = new MenuEntryEntity();
        menu1.setCode("MENU_A");
        menu1.setStatus(Status.ACTIVE);
        MenuEntryEntity menu2 = new MenuEntryEntity();
        menu2.setCode("MENU_B");
        menu2.setStatus(Status.ACTIVE);

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(Integer.valueOf(user.getId()), app.getId(), null))
                .thenReturn(List.of(menu1, menu2));

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

        when(authenticationHelper.getSub()).thenReturn("unknown-user");
        when(userRepository.findByExternalIdWithRolesAndPermissions("unknown-user"))
                .thenReturn(Optional.empty());

        GetCurrentUserApplicationMenusQuery query =
                new GetCurrentUserApplicationMenusQuery(null, "APP1");

        assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(query));

        verify(menuEntryRepository, never()).findActiveByApplicationIdAndUserIdFiltered(any(), any(), null);
    }

    // ------------------------------------------------------
    // 4. SUCCESS: no menus found → return empty list
    // ------------------------------------------------------
    @Test
    void handle_success_returnsEmptyList() {

        when(authenticationHelper.getSub()).thenReturn("ext-user-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("ext-user-1"))
                .thenReturn(Optional.of(user));
        when(applicationRepository.findByCodeAndStatusNotDeleted("APP1"))
                .thenReturn(app);

        when(menuEntryRepository.findActiveByApplicationIdAndUserIdFiltered(Integer.valueOf(user.getId()), app.getId(), null))
                .thenReturn(List.of());

        GetCurrentUserApplicationMenusQuery query =
                new GetCurrentUserApplicationMenusQuery(null, "APP1");

        ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

        assertTrue(response.getBody().isEmpty());
    }
}