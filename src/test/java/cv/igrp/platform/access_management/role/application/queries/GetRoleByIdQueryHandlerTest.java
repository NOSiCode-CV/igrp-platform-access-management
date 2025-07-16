package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GetRoleByIdQueryHandlerTest {

    @InjectMocks
    private GetRoleByIdQueryHandler underTest;
    @Mock
    private RoleEntityRepository roleRepository;
    @Mock
    private RoleMapper roleMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_When_ProvidedRoleId_NotFound() {
        //... Given
        int roleId = 100;
        GetRoleByIdQuery query = new GetRoleByIdQuery(roleId);

        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldNotCallMapper_WhenRoleNotFound() {
        //... Given
        int roleId = 100;
        GetRoleByIdQuery query = new GetRoleByIdQuery(roleId);
        RoleEntity savedRole = new RoleEntity();
        String roleName = "RoleName";
        savedRole.setName(roleName);
        savedRole.setId(roleId);
        Status roleStatus = Status.ACTIVE;
        savedRole.setStatus(roleStatus);
        RoleDTO expectedDto = new RoleDTO();
        expectedDto.setName(roleName);
        expectedDto.setId(roleId);
        expectedDto.setStatus(roleStatus);
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());

        verify(roleRepository, times(1)).findByIdAndStatusNot(roleId, Status.DELETED);
        verify(roleMapper, times(0)).mapToDto(savedRole);
    }

    @Test
    void itShouldReturnRoleDTO_WhenRoleExists() {
        //... Given
        int roleId = 100;
        GetRoleByIdQuery query = new GetRoleByIdQuery(roleId);
        RoleEntity savedRole = new RoleEntity();
        String roleName = "RoleName";
        savedRole.setName(roleName);
        savedRole.setId(roleId);
        Status roleStatus = Status.ACTIVE;
        savedRole.setStatus(roleStatus);
        RoleDTO expectedDto = new RoleDTO();
        expectedDto.setName(roleName);
        expectedDto.setId(roleId);
        expectedDto.setStatus(roleStatus);
        when(roleRepository.findByIdAndStatusNot(roleId, Status.DELETED))
                .thenReturn(Optional.of(savedRole));
        when(roleMapper.mapToDto(savedRole))
                .thenReturn(expectedDto);

        //... When
        ResponseEntity<RoleDTO> response = underTest.handle(query);

        //... Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(roleId, response.getBody().getId());
        assertNotNull(response.getBody());
        assertEquals(expectedDto.getName(), response.getBody().getName());

        verify(roleRepository, times(1)).findByIdAndStatusNot(roleId, Status.DELETED);
        verify(roleMapper, times(1)).mapToDto(savedRole);
    }
}