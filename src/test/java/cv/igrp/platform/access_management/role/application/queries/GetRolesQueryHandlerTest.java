package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class GetRolesQueryHandlerTest {

    @InjectMocks
    private GetRolesQueryHandler underTest;
    @Mock
    private RoleEntityRepository roleRepository;
    @Mock
    private RoleMapper roleMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldReturnEmptyList_WhenNoRolesAreFound() {
        // Given
        GetRolesQuery query = new GetRolesQuery();
        // When
        ResponseEntity<List<RoleDTO>> result = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result);
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());
    }

    @Test
    void itShouldReturnActiveAndInactiveRolesAsDTOs() {
        //... Given
        int activeRoleId = 100;
        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(activeRoleId);
        activeRole.setStatus(Status.ACTIVE);

        int inactiveRoleId = 200;
        RoleEntity inactiveRole = new RoleEntity();
        inactiveRole.setId(inactiveRoleId);
        inactiveRole.setStatus(Status.INACTIVE);

        ArrayList<RoleEntity> roles = new ArrayList<>();
        roles.add(activeRole);
        roles.add(inactiveRole);

        RoleDTO activeRoleDTO = new RoleDTO();
        activeRoleDTO.setId(1);
        activeRoleDTO.setName("Active Role");

        RoleDTO inactiveRoleDTO = new RoleDTO();
        inactiveRoleDTO.setId(2);
        inactiveRoleDTO.setName("Inactive Role");

        when(roleRepository.findByStatusIn(List.of(Status.ACTIVE, Status.INACTIVE))).thenReturn(roles);
        when(roleMapper.mapToDto(activeRole)).thenReturn(activeRoleDTO);
        when(roleMapper.mapToDto(inactiveRole)).thenReturn(inactiveRoleDTO);

        //... When
        GetRolesQuery query = new GetRolesQuery();
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);
        //... Then

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().contains(activeRoleDTO));
        assertTrue(response.getBody().contains(inactiveRoleDTO));

        verify(roleRepository).findByStatusIn(List.of(Status.ACTIVE, Status.INACTIVE));
        verify(roleMapper).mapToDto(activeRole);
        verify(roleMapper).mapToDto(inactiveRole);
    }
}