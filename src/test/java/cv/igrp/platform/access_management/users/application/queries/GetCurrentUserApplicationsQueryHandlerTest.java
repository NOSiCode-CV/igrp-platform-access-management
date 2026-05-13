package cv.igrp.platform.access_management.users.application.queries;
import static org.mockito.ArgumentMatchers.anyString;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetCurrentUserApplicationsQueryHandlerTest {

  @Mock
  private ApplicationEntityRepository applicationRepository;

  @Mock
  private IGRPUserEntityRepository userRepository;

  @Mock
  private ApplicationMapper applicationMapper;

  @Mock
  private AuthenticationHelper authenticationHelper;

  @InjectMocks
  private GetCurrentUserApplicationsQueryHandler handler;

  // -------------------------------------------------------------------------
  // SUCCESS: USER FOUND + APPLICATIONS RETURNED
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldReturnApplications_whenUserExists() {

    GetCurrentUserApplicationsQuery query = new GetCurrentUserApplicationsQuery("APP", null);

    IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000123");

    ApplicationEntity app1 = new ApplicationEntity();
    app1.setCode("APP_MAIN");

    ApplicationDTO dto1 = new ApplicationDTO();
    dto1.setCode("APP_MAIN");

    when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000123");
    when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(mockUser));
    when(applicationRepository.findByCurrentUserAndActiveFiltered(any(), any(), any()))
            .thenReturn(List.of(app1));
    when(applicationMapper.toDto(app1)).thenReturn(dto1);

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertNotNull(response);
    assertEquals(1, response.getBody().size());
    assertEquals("APP_MAIN", response.getBody().get(0).getCode());
  }

  // -------------------------------------------------------------------------
  // SUCCESS: FILTERING WORKS
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldFilterApplicationsByCode() {

    GetCurrentUserApplicationsQuery query = new GetCurrentUserApplicationsQuery("IGRP", null);

    IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000001");

    ApplicationEntity app1 = new ApplicationEntity();
    app1.setCode("IGRP_PLATFORM");

    ApplicationEntity app2 = new ApplicationEntity();
    app2.setCode("OTHER_APP");

    ApplicationDTO dto = new ApplicationDTO();
    dto.setCode("IGRP_PLATFORM");

    when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000001");
    when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(user));
    when(applicationRepository.findByCurrentUserAndActiveFiltered(any(), any(), any()))
            .thenReturn(List.of(app1, app2));

    when(applicationMapper.toDto(app1)).thenReturn(dto);

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertEquals(1, response.getBody().size());
    assertEquals("IGRP_PLATFORM", response.getBody().get(0).getCode());

    verify(applicationMapper, times(1)).toDto(app1);
    verify(applicationMapper, never()).toDto(app2);
  }

  // -------------------------------------------------------------------------
  // SUCCESS: EMPTY LIST
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldReturnEmptyList_whenNoApplications() {

    GetCurrentUserApplicationsQuery query = new GetCurrentUserApplicationsQuery(null, null);

    IGRPUserEntity user = new IGRPUserEntity();
        user.setId("00000000-0000-0000-0000-000000000888");

    when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000888");
    when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(user));
    when(applicationRepository.findByCurrentUserAndActiveFiltered(any(), any(), any()))
            .thenReturn(List.of());

    ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

    assertTrue(response.getBody().isEmpty());
  }

  // -------------------------------------------------------------------------
  // ERROR: USER NOT FOUND → UNAUTHORIZED
  // -------------------------------------------------------------------------
  @Test
  void handle_shouldThrow_whenUserNotFound() {

    GetCurrentUserApplicationsQuery query = new GetCurrentUserApplicationsQuery(null, null);

    when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000002");
    when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.empty());

    assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
  }
}