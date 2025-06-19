package cv.igrp.platform.access_management.permission.application.queries.handlers;

import cv.igrp.platform.access_management.permission.application.queries.queries.GetPermissionByApplicationIdQuery;
import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.models.Application;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetPermissionByApplicationIdQueryHandlerTest {

    @InjectMocks
    private GetPermissionByApplicationIdQueryHandler underTest;
    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldFilterByApplicationIdOnly_AndIgnoreOthers() {
        // Given
        int targetAppId = 100;
        int otherAppId = 200;

        Application targetApp = new Application();
        targetApp.setId(targetAppId);

        Application otherApp = new Application();
        otherApp.setId(otherAppId);

        Permission p1 = new Permission();
        p1.setId(1);
        p1.setName("P1");
        p1.setStatus(Status.ACTIVE);
        p1.setApplication(targetApp);

        Permission p2 = new Permission();
        p2.setId(2);
        p2.setName("P2");
        p2.setStatus(Status.INACTIVE);
        p2.setApplication(otherApp);

        Permission p3 = new Permission();
        p3.setId(3);
        p3.setName("P3");
        p3.setStatus(Status.ACTIVE);
        p3.setApplication(targetApp);

        List<Permission> allPermissions = List.of(p1, p2, p3);

        PermissionDTO dto1 = new PermissionDTO();
        dto1.setId(1);
        dto1.setName("P1");

        PermissionDTO dto3 = new PermissionDTO();
        dto3.setId(3);
        dto3.setName("P3");

        when(permissionRepository.findAll()).thenReturn(allPermissions);
        when(permissionMapper.mapToDTO(p1)).thenReturn(dto1);
        when(permissionMapper.mapToDTO(p3)).thenReturn(dto3);

        GetPermissionByApplicationIdQuery query = new GetPermissionByApplicationIdQuery(targetAppId, null);

        // When
        ResponseEntity<List<PermissionDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PermissionDTO> result = response.getBody();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(dto1));
        assertTrue(result.contains(dto3));

        verify(permissionMapper, times(1)).mapToDTO(p1);
        verify(permissionMapper, times(1)).mapToDTO(p3);
        verify(permissionMapper, never()).mapToDTO(p2);
    }

    @Test
    void itShouldIgnorePermissionsWithDeletedStatus() {
        // Given
        int targetAppId = 100;

        Application targetApp = new Application();
        targetApp.setId(targetAppId);

        Permission p1 = new Permission();
        p1.setId(1);
        p1.setName("P1");
        p1.setStatus(Status.ACTIVE);
        p1.setApplication(targetApp);

        Permission p3 = new Permission();
        p3.setId(3);
        p3.setName("P3");
        p3.setStatus(Status.ACTIVE);
        p3.setApplication(targetApp);

        Permission p4 = new Permission();
        p4.setId(4);
        p4.setName("P4");
        p4.setStatus(Status.DELETED);
        p4.setApplication(targetApp);
        List<Permission> allPermissions = List.of(p1, p3, p4);

        PermissionDTO dto1 = new PermissionDTO();
        dto1.setId(1);
        dto1.setName("P1");

        PermissionDTO dto3 = new PermissionDTO();
        dto3.setId(3);
        dto3.setName("P3");

        when(permissionRepository.findAll()).thenReturn(allPermissions);
        when(permissionMapper.mapToDTO(p1)).thenReturn(dto1);
        when(permissionMapper.mapToDTO(p3)).thenReturn(dto3);

        GetPermissionByApplicationIdQuery query = new GetPermissionByApplicationIdQuery(targetAppId, null);

        // When
        ResponseEntity<List<PermissionDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PermissionDTO> result = response.getBody();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(dto1));
        assertTrue(result.contains(dto3));

        verify(permissionMapper, times(1)).mapToDTO(p1);
        verify(permissionMapper, times(1)).mapToDTO(p3);
        verify(permissionMapper, never()).mapToDTO(p4);
    }

    @Test
    void itShouldReturnPermissions_WhenApplicationIdMatchesAndStatusIsActiveOrInactive() {
        // Given
        int applicationId = 100;

        Application app = new Application();
        app.setId(applicationId);

        Permission activePermission = new Permission();
        activePermission.setId(1);
        activePermission.setName("ACTIVE");
        activePermission.setStatus(Status.ACTIVE);
        activePermission.setApplication(app);

        Permission inactivePermission = new Permission();
        inactivePermission.setId(2);
        inactivePermission.setName("INACTIVE");
        inactivePermission.setStatus(Status.INACTIVE);
        inactivePermission.setApplication(app);

        Permission deletedPermission = new Permission();
        deletedPermission.setId(3);
        deletedPermission.setName("DELETED");
        deletedPermission.setStatus(Status.DELETED);
        deletedPermission.setApplication(app);

        List<Permission> allPermissions = List.of(activePermission, inactivePermission, deletedPermission);

        PermissionDTO dto1 = new PermissionDTO();
        dto1.setId(1);
        dto1.setName("ACTIVE");

        PermissionDTO dto2 = new PermissionDTO();
        dto2.setId(2);
        dto2.setName("INACTIVE");

        when(permissionRepository.findAll()).thenReturn(allPermissions);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(dto1);
        when(permissionMapper.mapToDTO(inactivePermission)).thenReturn(dto2);

        GetPermissionByApplicationIdQuery query = new GetPermissionByApplicationIdQuery(applicationId, null);

        // When
        ResponseEntity<List<PermissionDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<PermissionDTO> result = response.getBody();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(dto1));
        assertTrue(result.contains(dto2));

        verify(permissionMapper, times(1)).mapToDTO(activePermission);
        verify(permissionMapper, times(1)).mapToDTO(inactivePermission);
        verify(permissionMapper, never()).mapToDTO(deletedPermission);
    }
}