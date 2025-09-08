package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.menu.application.dto.MenuEntryDTO;
import cv.igrp.platform.access_management.menu.mapper.MenuEntryMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.*;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.MenuEntryEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetAppMenusQueryHandlerTest {

  @InjectMocks
  private GetAppMenusQueryHandler handler;

  @Mock
  private AuthenticationHelper authenticationHelper;

  @Mock
  private IGRPUserEntityRepository userRepository;

  @Mock
  private ApplicationEntityRepository applicationRepository;

  @Mock
  private MenuEntryEntityRepository menuEntryRepository;

  @Mock
  private MenuEntryMapper menuEntryMapper;

  private IGRPUserEntity user;
  private ApplicationEntity app;
  private RoleEntity role;
  private PermissionEntity permission;
  private MenuEntryEntity menu;
  private MenuEntryDTO menuDTO;

  @BeforeEach
  void setUp() {
    permission = new PermissionEntity();
    permission.setId(100);
    permission.setName("view_menu");
    permission.setStatus(Status.ACTIVE);

    role = new RoleEntity();
    role.setPermissions(Set.of(permission));
    role.setStatus(Status.ACTIVE);

    user = new IGRPUserEntity();
    user.setUsername("superadmin");
    user.setRoles(List.of(role));

    app = new ApplicationEntity();
    app.setId(1);
    app.setCode("APP_IGRP");
    app.setStatus(Status.ACTIVE);

    menu = new MenuEntryEntity();
    menu.setId(10);
    menu.setStatus(Status.ACTIVE);
    menu.setPermissions(List.of(permission));

    menuDTO = new MenuEntryDTO();
    menuDTO.setId(10);
    menuDTO.setName("Test Menu");
    menuDTO.setStatus(Status.ACTIVE);
  }

  @Test
  void testHandle_ReturnsAccessibleMenus() {
    // Given
    String appCode = "APP_IGRP";
    GetAppMenusQuery query = new GetAppMenusQuery(appCode);

    when(authenticationHelper.getPreferredUsername()).thenReturn("superadmin");
    when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCode(appCode)).thenReturn(Optional.of(app));
    when(menuEntryRepository.findByApplicationIdAndStatus(app, Status.ACTIVE)).thenReturn(List.of(menu));
    when(menuEntryMapper.toDTO(menu)).thenReturn(menuDTO);

    // When
    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(query);

    // Then
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(1, response.getBody().size());
    assertEquals("Test Menu", response.getBody().get(0).getName());
  }

  @Test
  void testHandle_UserNotFound_Returns404() {
    when(authenticationHelper.getPreferredUsername()).thenReturn("unknown");
    when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_IGRP"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void testHandle_AppNotFound_Returns404() {
    when(authenticationHelper.getPreferredUsername()).thenReturn("superadmin");
    when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCode("APP_UNKNOWN")).thenReturn(Optional.empty());

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_UNKNOWN"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void testHandle_UserHasNoPermissions_ReturnsEmptyList() {

    when(authenticationHelper.getPreferredUsername()).thenReturn("superadmin");
    when(userRepository.findByUsername("superadmin")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCode("APP_IGRP")).thenReturn(Optional.of(app));

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_IGRP"));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }
}