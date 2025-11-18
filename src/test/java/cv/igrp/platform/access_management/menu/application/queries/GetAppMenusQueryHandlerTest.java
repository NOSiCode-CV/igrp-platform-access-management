package cv.igrp.platform.access_management.menu.application.queries;

import cv.igrp.platform.access_management.shared.application.constants.MenuEntryType;
import cv.igrp.platform.access_management.shared.application.dto.MenuEntryDTO;
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
  private MenuEntryEntity menu;
  private MenuEntryDTO menuDTO;

  @BeforeEach
  void setUp() {
    role = new RoleEntity();
    role.setId(100);
    role.setName("admin");
    role.setStatus(Status.ACTIVE);

    user = new IGRPUserEntity();
    user.setExternalId("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454");
    user.getRoles().add(role);

    app = new ApplicationEntity();
    app.setId(1);
    app.setCode("APP_IGRP");
    app.setStatus(Status.ACTIVE);

    menu = new MenuEntryEntity();
    menu.setId(10);
    menu.setStatus(Status.ACTIVE);
    menu.getRoles().add(role);

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

    when(authenticationHelper.getPreferredUsername()).thenReturn("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454");
    when(userRepository.findByExternalId("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCodeAndStatusNot(appCode, Status.DELETED)).thenReturn(Optional.of(app));
    when(menuEntryRepository.findByApplicationIdAndTypeInAndStatusIn(app, List.of(MenuEntryType.MENU_PAGE, MenuEntryType.SYSTEM_PAGE, MenuEntryType.EXTERNAL_PAGE), List.of(Status.ACTIVE))).thenReturn(List.of(menu));
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
    when(userRepository.findByExternalId("unknown")).thenReturn(Optional.empty());

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_IGRP"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void testHandle_AppNotFound_Returns404() {
    when(authenticationHelper.getPreferredUsername()).thenReturn("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454");
    when(userRepository.findByExternalId("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCodeAndStatusNot("APP_UNKNOWN", Status.DELETED)).thenReturn(Optional.empty());

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_UNKNOWN"));

    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    assertNull(response.getBody());
  }

  @Test
  void testHandle_UserHasNoRoles_ReturnsEmptyList() {

    when(authenticationHelper.getPreferredUsername()).thenReturn("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454");
    when(userRepository.findByExternalId("f8c3de3d-1fea-4d7c-a8b0-29f63c4c3454")).thenReturn(Optional.of(user));
    when(applicationRepository.findByCodeAndStatusNot("APP_IGRP", Status.DELETED)).thenReturn(Optional.of(app));

    ResponseEntity<List<MenuEntryDTO>> response = handler.handle(new GetAppMenusQuery("APP_IGRP"));

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertTrue(response.getBody().isEmpty());
  }
}