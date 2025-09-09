package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetRolesByPermissionIDQueryHandlerTest {

    @InjectMocks
    private GetRolesByPermissionIDQueryHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private RoleMapper roleMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_ProvidedPermissionId_DoesNotExist() {
        //... Given
        int permissionId = 100;
        String permissionName = "test";
        GetRolesByPermissionIDQuery query = new GetRolesByPermissionIDQuery(permissionName);

        when(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldThrowException_WhenPermissionIsDeleted() {
        //... Given
        int permissionId = 100;
        String permissionName = "test";
        PermissionEntity deletedPermission = new PermissionEntity();
        deletedPermission.setId(permissionId);
        deletedPermission.setName(permissionName);
        deletedPermission.setStatus(Status.DELETED);

        when(permissionRepository.findByName(permissionName))
                .thenReturn(Optional.of(deletedPermission));

        GetRolesByPermissionIDQuery query = new GetRolesByPermissionIDQuery(permissionName);
        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        verifyNoInteractions(roleMapper);
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldReturnEmptyList_WhenPermissionHasNoRoles() {
        // Given
        int permissionId = 300;
        String permissionName = "test";
        GetRolesByPermissionIDQuery query = new GetRolesByPermissionIDQuery(permissionName);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(permissionId);
        permission.setName(permissionName);
        permission.setStatus(Status.ACTIVE);
        permission.setRoles(null);

        when(permissionRepository.findByName(permissionName)).thenReturn(Optional.of(permission));

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RoleDTO> result = response.getBody();
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(roleMapper, never()).mapToDto(any());
    }

    @Test
    void itShouldReturnOnlyActiveOrInactiveRoles() {
        // Given
        int permissionId = 101;
        String permissionName = "test";
        GetRolesByPermissionIDQuery query = new GetRolesByPermissionIDQuery(permissionName);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(permissionId);
        permission.setName(permissionName);
        permission.setStatus(Status.ACTIVE);

        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(1);
        activeRole.setName("admin");
        activeRole.setStatus(Status.ACTIVE);

        RoleEntity inactiveRole = new RoleEntity();
        inactiveRole.setId(2);
        inactiveRole.setName("inactive");
        inactiveRole.setStatus(Status.INACTIVE);

        RoleEntity deletedRole = new RoleEntity();
        deletedRole.setId(3);
        deletedRole.setName("deleted");
        deletedRole.setStatus(Status.DELETED);

        permission.setRoles(Set.of(activeRole, inactiveRole, deletedRole));

        RoleDTO activeDTO = new RoleDTO();
        activeDTO.setId(1);
        activeDTO.setName("active");
        RoleDTO inactiveDTO = new RoleDTO();
        inactiveDTO.setId(2);
        inactiveDTO.setName("inactive");

        when(permissionRepository.findByName(permissionName)).thenReturn(Optional.of(permission));
        when(roleMapper.mapToDto(activeRole)).thenReturn(activeDTO);
        when(roleMapper.mapToDto(inactiveRole)).thenReturn(inactiveDTO);

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RoleDTO> result = response.getBody();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains(activeDTO));
        assertTrue(result.contains(inactiveDTO));

        verify(roleMapper, times(1)).mapToDto(activeRole);
        verify(roleMapper, times(1)).mapToDto(inactiveRole);
        verify(roleMapper, never()).mapToDto(deletedRole);
    }

    @Test
    void itShouldReturnMappedRoles_WhenPermissionIsFoundAndActive() {
        // Given
        int permissionId = 200;
        String permissionName = "test";
        GetRolesByPermissionIDQuery query = new GetRolesByPermissionIDQuery(permissionName);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(permissionId);
        permission.setName(permissionName);
        permission.setStatus(Status.ACTIVE);

        RoleEntity role1 = new RoleEntity();
        role1.setId(1);
        role1.setName("admin");
        role1.setStatus(Status.ACTIVE);

        RoleEntity role2 = new RoleEntity();
        role2.setId(2);
        role2.setName("inactive");
        role2.setStatus(Status.INACTIVE);

        permission.setRoles(Set.of(role1, role2));

        RoleDTO dto1 = new RoleDTO();
        dto1.setId(1);
        dto1.setName("admin");

        RoleDTO dto2 = new RoleDTO();
        dto2.setId(2);
        dto2.setName("inactive");

        when(permissionRepository.findByName(permissionName)).thenReturn(Optional.of(permission));
        when(roleMapper.mapToDto(role1)).thenReturn(dto1);
        when(roleMapper.mapToDto(role2)).thenReturn(dto2);

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<RoleDTO> result = response.getBody();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsAll(List.of(dto1, dto2)));

        verify(roleMapper, times(1)).mapToDto(role1);
        verify(roleMapper, times(1)).mapToDto(role2);
    }
}