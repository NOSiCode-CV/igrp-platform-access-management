package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.app.mapper.ApplicationMapper;
import cv.igrp.platform.access_management.shared.application.dto.ApplicationDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ApplicationEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ApplicationEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;

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
public class GetUserApplicationsQueryHandlerTest {

    @Mock
    private ApplicationEntityRepository applicationRepository;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private ApplicationMapper applicationMapper;

    @InjectMocks
    private GetUserApplicationsQueryHandler handler;

    // -------------------------------------------------------------------------
    // SUCCESS: USER FOUND + APPLICATIONS RETURNED
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldReturnApplications_whenUserExists() {

        GetUserApplicationsQuery query = new GetUserApplicationsQuery("APP", null, 10);

        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(10);

        ApplicationEntity app = new ApplicationEntity();
        app.setCode("APP_MAIN");

        ApplicationDTO dto = new ApplicationDTO();
        dto.setCode("APP_MAIN");

        when(userRepository.findById(10)).thenReturn(Optional.of(user));
        when(applicationRepository.findByUserIdAndStatusNotDeleted(user)).thenReturn(List.of(app));
        when(applicationMapper.toDto(app)).thenReturn(dto);

        ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("APP_MAIN", response.getBody().get(0).getCode());
    }

    // -------------------------------------------------------------------------
    // SUCCESS: FILTERING WORKS
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldFilterApplicationsByCode() {

        GetUserApplicationsQuery query = new GetUserApplicationsQuery("CRM", null, 99);

        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(99);

        ApplicationEntity app1 = new ApplicationEntity();
        app1.setCode("CRM_PORTAL");

        ApplicationEntity app2 = new ApplicationEntity();
        app2.setCode("OTHER");

        ApplicationDTO dto = new ApplicationDTO();
        dto.setCode("CRM_PORTAL");

        when(userRepository.findById(99)).thenReturn(Optional.of(user));
        when(applicationRepository.findByUserIdAndStatusNotDeleted(user)).thenReturn(List.of(app1, app2));
        when(applicationMapper.toDto(app1)).thenReturn(dto);

        ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CRM_PORTAL", response.getBody().get(0).getCode());

        verify(applicationMapper).toDto(app1);
        verify(applicationMapper, never()).toDto(app2);
    }

    // -------------------------------------------------------------------------
    // SUCCESS: EMPTY LIST
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldReturnEmptyList_whenNoApplications() {

        GetUserApplicationsQuery query = new GetUserApplicationsQuery(null, null, 5);

        IGRPUserEntity user = new IGRPUserEntity();
        user.setId(5);

        when(userRepository.findById(5)).thenReturn(Optional.of(user));
        when(applicationRepository.findByUserIdAndStatusNotDeleted(user)).thenReturn(List.of());

        ResponseEntity<List<ApplicationDTO>> response = handler.handle(query);

        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    // -------------------------------------------------------------------------
    // ERROR: USER NOT FOUND
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldThrow_whenUserNotFound() {

        GetUserApplicationsQuery query = new GetUserApplicationsQuery(null, null, 777);

        when(userRepository.findById(777)).thenReturn(Optional.empty());

        assertThrows(IgrpResponseStatusException.class, () -> handler.handle(query));
    }
}