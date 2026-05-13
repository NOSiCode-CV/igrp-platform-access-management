package cv.igrp.platform.access_management.users.application.queries;
import static org.mockito.ArgumentMatchers.anyString;

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
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
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
        mockUser.setId("00000000-0000-0000-0000-000000000123");

        DepartmentEntity dep1 = new DepartmentEntity();
        dep1.setCode("DEP_A");

        DepartmentDTO dto1 = new DepartmentDTO();
        dto1.setCode("DEP_A");

        when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000123");
        when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(mockUser));
        when(departmentRepository.findByCurrentUserAndNotDeletedFiltered(any(), any()))
                .thenReturn(List.of(dep1));
        when(departmentMapper.toDto(dep1)).thenReturn(dto1);

        ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("DEP_A", response.getBody().get(0).getCode());

        verify(userRepository).findByIdWithRolesAndPermissions(anyString());
        verify(departmentRepository).findByCurrentUserAndNotDeletedFiltered(mockUser.getId(), query.getDepartmentCode());
        verify(departmentMapper).toDto(dep1);
    }

    // -------------------------------------------------------------------------
    // SUCCESS SCENARIO: FILTERING WORKS
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldFilterDepartmentsByCode() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery("FIN");

        IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000999");

        DepartmentEntity dep1 = new DepartmentEntity();
        dep1.setCode("FIN_DEPT");

        DepartmentDTO dto1 = new DepartmentDTO();
        dto1.setCode("FIN_DEPT");

        when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000999");
        when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(mockUser));
        // Filter is pushed down to the repository — it returns only matching rows.
        when(departmentRepository.findByCurrentUserAndNotDeletedFiltered(any(), org.mockito.ArgumentMatchers.eq("FIN")))
                .thenReturn(List.of(dep1));

        when(departmentMapper.toDto(dep1)).thenReturn(dto1);

        ResponseEntity<List<DepartmentDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("FIN_DEPT", response.getBody().get(0).getCode());

        verify(departmentMapper, times(1)).toDto(dep1);
        verify(departmentRepository).findByCurrentUserAndNotDeletedFiltered(any(), org.mockito.ArgumentMatchers.eq("FIN"));
    }

    // -------------------------------------------------------------------------
    // SUCCESS SCENARIO: NO DEPARTMENTS FOUND
    // -------------------------------------------------------------------------
    @Test
    void handle_shouldReturnEmptyList_whenUserHasNoDepartments() {

        GetCurrentUserDepartmentsQuery query = new GetCurrentUserDepartmentsQuery(null);

        IGRPUserEntity mockUser = new IGRPUserEntity();
        mockUser.setId("00000000-0000-0000-0000-000000000111");

        when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000000111");
        when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.of(mockUser));
        when(departmentRepository.findByCurrentUserAndNotDeletedFiltered(any(), any()))
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

        when(authenticationHelper.getSub()).thenReturn("00000000-0000-0000-0000-000000001122");
        when(userRepository.findByIdWithRolesAndPermissions(anyString())).thenReturn(Optional.empty());

        IgrpResponseStatusException ex = assertThrows(
                IgrpResponseStatusException.class,
                () -> handler.handle(query)
        );

        assertEquals("User not found", ex.getBody().getTitle());
        assertNotNull(ex.getMessage());
    }
}