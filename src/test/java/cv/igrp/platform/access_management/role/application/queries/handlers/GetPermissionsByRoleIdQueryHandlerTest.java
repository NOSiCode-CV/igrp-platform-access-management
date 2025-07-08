package cv.igrp.platform.access_management.role.application.queries.handlers;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.role.application.queries.queries.GetPermissionsByRoleIdQuery;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.domain.models.Permission;
import cv.igrp.platform.access_management.shared.domain.models.Role;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.RoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetPermissionsByRoleIdQueryHandlerTest {

    @InjectMocks
    private GetPermissionsByRoleIdQueryHandler underTest;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionMapper permissionMapper;


    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_WhenProvidedRoleId_NotFound() {
        //... Given
        int roleId = 100;

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.empty());

        GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery(roleId);

        // When
        IgrpResponseStatusException ex = assertThrows(IgrpResponseStatusException.class,
                () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getBody().getStatus());
    }

    @Test
    void itShouldCallMapperOnlyForValidPermissions() {
        // Given
        int roleId = 1;
        GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery(roleId);

        Permission activePermission = new Permission();
        activePermission.setId(1);
        activePermission.setName("Read");
        activePermission.setStatus(Status.ACTIVE);

        Permission deletedPermission = new Permission();
        deletedPermission.setId(2);
        deletedPermission.setName("Write");
        deletedPermission.setStatus(Status.DELETED);

        Set<Permission> permissions = new HashSet<>(List.of(activePermission, deletedPermission));

        Role role = new Role();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(permissions);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));

        PermissionDTO activePermissionDTO = new PermissionDTO();
        activePermissionDTO.setId(1);
        when(permissionMapper.mapToDTO(activePermission)).thenReturn(activePermissionDTO);

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(query);

        // Then
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(activePermissionDTO, result.getBody().getFirst());

        verify(permissionMapper).mapToDTO(activePermission);
        verify(permissionMapper, never()).mapToDTO(deletedPermission);
    }

    @Test
    void itShouldNotReturnDeletedPermissions_EvenIfTheyAreInTheRole() {
        // Given
        int roleId = 1;
        GetPermissionsByRoleIdQuery query = new GetPermissionsByRoleIdQuery(roleId);

        Permission deletedPermission1 = new Permission();
        deletedPermission1.setId(1);
        deletedPermission1.setName("Permission A");
        deletedPermission1.setStatus(Status.DELETED);

        Permission deletedPermission2 = new Permission();
        deletedPermission2.setId(2);
        deletedPermission2.setName("Permission B");
        deletedPermission2.setStatus(Status.DELETED);

        Role role = new Role();
        role.setId(roleId);
        role.setStatus(Status.ACTIVE);
        role.setPermissions(new HashSet<>(List.of(deletedPermission1, deletedPermission2)));

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED)).thenReturn(Optional.of(role));

        // When
        ResponseEntity<List<PermissionDTO>> result = underTest.handle(query);

        // Then
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());

        verify(permissionMapper, never()).mapToDTO(any());
    }
}