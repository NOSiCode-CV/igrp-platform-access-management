package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.department.mapper.DepartmentMapper;
import cv.igrp.platform.access_management.shared.application.dto.DepartmentDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
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
public class GetCurrentUserDepartmentsQueryHandlerTest {

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private DepartmentMapper departmentMapper;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @InjectMocks
    private GetCurrentUserDepartmentsQueryHandler handler;

    // -------------------------------------------------------------------------
    // SUCCESS SCENARIO: USER FOUND + DEPARTMENTS RETURNED
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldReturnDepartments_whenUserExists() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery("DEP");

        IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setExternalId("sub123");

        DepartmentEntity dep1 = new DepartmentEntity();
        dep1.setCode("DEP_A");

        DepartmentDTO dto1 = new DepartmentDTO();
        dto1.setCode("DEP_A");

        when(authenticationHelper.getSub()).thenReturn("sub123");
        when(userRepository.findByExternalIdWithRolesAndPermissions("sub123")).thenReturn(Optional.of(mockUser));
        when(departmentRepository.findByUserAndNotDeletedFiltered(Integer.valueOf(mockUser.getId()), null))
                .thenReturn(List.of(dep1));
        when(departmentMapper.toDto(dep1)).thenReturn(dto1);

        ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("DEP_A", response.getBody().get(0).getCode());

        verify(userRepository).findByExternalIdWithRolesAndPermissions("sub123");
        verify(departmentRepository).findByUserAndNotDeletedFiltered(Integer.valueOf(mockUser.getId()), null);
        verify(departmentMapper).toDto(dep1);
    }

    // -------------------------------------------------------------------------
    // SUCCESS SCENARIO: FILTERING WORKS
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldFilterDepartmentsByCode() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery("FIN");

        IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setExternalId("sub999");

        DepartmentEntity dep1 = new DepartmentEntity();
        dep1.setCode("FIN_DEPT");

        DepartmentEntity dep2 = new DepartmentEntity();
        dep2.setCode("HR_DEPT");

        DepartmentDTO dto1 = new DepartmentDTO();
        dto1.setCode("FIN_DEPT");

        when(authenticationHelper.getSub()).thenReturn("sub999");
        when(userRepository.findByExternalIdWithRolesAndPermissions("sub999")).thenReturn(Optional.of(mockUser));
        when(departmentRepository.findByUserAndNotDeletedFiltered(Integer.valueOf(mockUser.getId()), null))
                .thenReturn(List.of(dep1, dep2)); // Only dep1 should pass the filter

        when(departmentMapper.toDto(dep1)).thenReturn(dto1);

        ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("FIN_DEPT", response.getBody().get(0).getCode());

        verify(departmentMapper, times(1)).toDto(dep1);
        verify(departmentMapper, never()).toDto(dep2);
    }

    // -------------------------------------------------------------------------
    // SUCCESS SCENARIO: NO DEPARTMENTS FOUND
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldReturnEmptyList_whenUserHasNoDepartments() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery(null);

        IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setExternalId("sub111");

        when(authenticationHelper.getSub()).thenReturn("sub111");
        when(userRepository.findByExternalIdWithRolesAndPermissions("sub111")).thenReturn(Optional.of(mockUser));
        when(departmentRepository.findByUserAndNotDeletedFiltered(Integer.valueOf(mockUser.getId()), null))
                .thenReturn(List.of());

        ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    // -------------------------------------------------------------------------
    // ERROR SCENARIO: USER NOT FOUND → UNAUTHORIZED
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldThrowUnauthorized_whenUserNotFound() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery(null);

        when(authenticationHelper.getSub()).thenReturn("missing1122");
        when(userRepository.findByExternalIdWithRolesAndPermissions("missing1122")).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> handler.handle(query)
        );

        assertEquals("User not found", ex.getBody().getTitle());
        assertNotNull(ex.getMessage());
    }
}