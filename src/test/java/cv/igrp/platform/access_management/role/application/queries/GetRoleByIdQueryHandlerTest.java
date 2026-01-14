package cv.igrp.platform.access_management.role.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.constants.DepartmentStatus;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
    private DepartmentEntityRepository departmentRepository;

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
        String deptCode = "DEPT";
        GetRoleByIdQuery query = new GetRoleByIdQuery(deptCode, roleId);

        DepartmentEntity department = new DepartmentEntity();
        department.setCode(deptCode);
        department.setStatus(DepartmentStatus.ACTIVE);

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(department);
        when(roleRepository.findByDepartmentAndIdAndStatusNot(department, roleId, Status.DELETED))
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
        String deptCode = "DEPT";
        GetRoleByIdQuery query = new GetRoleByIdQuery(deptCode, roleId);
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

        var dept = new DepartmentEntity();
        dept.setStatus(DepartmentStatus.ACTIVE);
        dept.setCode(deptCode);

        when(departmentRepository.findByCodeAndStatusNotDeleted(deptCode)).thenReturn(dept);
        when(roleRepository.findByDepartmentAndIdAndStatusNot(dept, roleId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());

        verify(roleRepository, times(1)).findByDepartmentAndIdAndStatusNot(dept,roleId, Status.DELETED);
        verify(roleMapper, times(0)).mapToDto(savedRole);
    }

    @Test
    void itShouldReturnRoleDTO_WhenRoleExists() {
        //... Given
        int roleId = 100;
        String deptCode = "DEPT";
        GetRoleByIdQuery query = new GetRoleByIdQuery(deptCode, roleId);
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

        var dept = departmentRepository.findByCodeAndStatusNotDeleted(deptCode);
        when(roleRepository.findByDepartmentAndIdAndStatusNot(dept, roleId, Status.DELETED))
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

        verify(roleRepository, times(1)).findByDepartmentAndIdAndStatusNot(dept, roleId, Status.DELETED);
        verify(roleMapper, times(1)).mapToDto(savedRole);
    }
}