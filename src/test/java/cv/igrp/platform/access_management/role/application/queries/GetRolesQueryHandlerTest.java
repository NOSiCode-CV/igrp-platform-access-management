package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRolesQueryHandlerTest {

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
        when(roleRepository.findAll(any(Specification.class))).thenReturn(List.of());

        // When
        ResponseEntity<List<RoleDTO>> result = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());
        verify(roleRepository).findAll(any(Specification.class));
        verifyNoInteractions(roleMapper);
    }

    @Test
    void itShouldReturnActiveAndInactiveRolesAsDTOs() {
        // Given
        RoleEntity activeRole = new RoleEntity();
        activeRole.setId(100);
        activeRole.setStatus(Status.ACTIVE);

        RoleEntity inactiveRole = new RoleEntity();
        inactiveRole.setId(200);
        inactiveRole.setStatus(Status.INACTIVE);

        RoleDTO activeRoleDTO = new RoleDTO();
        activeRoleDTO.setId(1);
        activeRoleDTO.setName("Active Role");

        RoleDTO inactiveRoleDTO = new RoleDTO();
        inactiveRoleDTO.setId(2);
        inactiveRoleDTO.setName("Inactive Role");

        when(roleRepository.findAll(any(Specification.class))).thenReturn(List.of(activeRole, inactiveRole));
        when(roleMapper.mapToDto(activeRole)).thenReturn(activeRoleDTO);
        when(roleMapper.mapToDto(inactiveRole)).thenReturn(inactiveRoleDTO);

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(new GetRolesQuery());

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertTrue(response.getBody().contains(activeRoleDTO));
        assertTrue(response.getBody().contains(inactiveRoleDTO));

        verify(roleRepository).findAll(any(Specification.class));
        verify(roleMapper).mapToDto(activeRole);
        verify(roleMapper).mapToDto(inactiveRole);
    }

    @Test
    void itShouldApplyDepartmentFilter() {
        // Given
        GetRolesQuery query = new GetRolesQuery();
        query.setDepartmentCode("HR");

        RoleEntity role = new RoleEntity();
        role.setId(300);
        role.setStatus(Status.ACTIVE);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(3);
        roleDTO.setName("HR Role");

        when(roleRepository.findAll(any(Specification.class))).thenReturn(List.of(role));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(roleDTO, response.getBody().get(0));

        // Capture specification
        ArgumentCaptor<Specification<RoleEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(roleRepository).findAll(specCaptor.capture());
        assertNotNull(specCaptor.getValue());

        verify(roleMapper).mapToDto(role);
    }

    @Test
    void itShouldApplyUsernameFilter() {
        // Given
        GetRolesQuery query = new GetRolesQuery();
        query.setName("manager");

        RoleEntity role = new RoleEntity();
        role.setId(400);
        role.setStatus(Status.ACTIVE);

        RoleDTO roleDTO = new RoleDTO();
        roleDTO.setId(4);
        roleDTO.setName("Manager Role");

        when(roleRepository.findAll(any(Specification.class))).thenReturn(List.of(role));
        when(roleMapper.mapToDto(role)).thenReturn(roleDTO);

        // When
        ResponseEntity<List<RoleDTO>> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(roleDTO, response.getBody().get(0));

        // Verify repository interaction
        verify(roleRepository).findAll(any(Specification.class));
        verify(roleMapper).mapToDto(role);
    }
}
