package cv.igrp.platform.access_management.users.application.queries;

import cv.igrp.platform.access_management.role.domain.service.RoleMapper;
import cv.igrp.platform.access_management.shared.application.dto.RoleDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.IGRPUserEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.RoleEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.IGRPUserEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.RoleEntityRepository;
import cv.igrp.platform.access_management.shared.security.AuthenticationHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserDepartmentRolesQueryHandlerTest {

    @Mock
    private RoleEntityRepository roleRepository;

    @Mock
    private IGRPUserEntityRepository userRepository;

    @Mock
    private DepartmentEntityRepository departmentRepository;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AuthenticationHelper authenticationHelper;

    @InjectMocks
    private GetCurrentUserDepartmentRolesQueryHandler handler;

    private IGRPUserEntity user;
    private DepartmentEntity department;
    private RoleDTO roleDTO1;
    private RoleDTO roleDTO2;

    @BeforeEach
    void setUp() {
        user = new IGRPUserEntity();
        user.setId(1);
        user.setExternalId("user-ext-1");

        department = new DepartmentEntity();
        department.setId(1);
        department.setCode("DEPT1");
        
        roleDTO1 = new RoleDTO();
        roleDTO1.setCode("ROLE_A");
        roleDTO1.setName("Role A");
        roleDTO1.setDepartmentCode("DEPT1");
        
        roleDTO2 = new RoleDTO();
        roleDTO2.setCode("ROLE_B");
        roleDTO2.setName("Role B");
        roleDTO2.setDepartmentCode("DEPT1");
        
    }

    // ---------------------------------------------------------------------
    // 1. SUCCESS CASE — User exists, department exists, returns roles
    // ---------------------------------------------------------------------
    @Test
    void handle_success_returnsRoles() {

        GetCurrentUserDepartmentRolesQuery query =
                new GetCurrentUserDepartmentRolesQuery(null, "DEPT1");

        when(authenticationHelper.getSub()).thenReturn("user-ext-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("user-ext-1"))
                .thenReturn(Optional.of(user));
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT1"))
                .thenReturn(department);

        RoleEntity roleA = new RoleEntity();
        RoleEntity roleB = new RoleEntity();

        when(roleRepository.findByDepartmentIdAndUserIdAndStatusNotDeleted(user, department))
                .thenReturn(List.of(roleA, roleB));

        when(roleMapper.mapToDto(roleA))
                .thenReturn(roleDTO1);
        when(roleMapper.mapToDto(roleB))
                .thenReturn(roleDTO2);

        ResponseEntity<List<RoleDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertEquals(2, response.getBody().size());
        assertEquals("ROLE_A", response.getBody().get(0).getCode());
        assertEquals("ROLE_B", response.getBody().get(1).getCode());

        verify(roleRepository).findByDepartmentIdAndUserIdAndStatusNotDeleted(user, department);
    }

    // ---------------------------------------------------------------------
    // 2. SUCCESS CASE — No roles found → returns empty list
    // ---------------------------------------------------------------------
    @Test
    void handle_success_returnsEmptyList() {

        GetCurrentUserDepartmentRolesQuery query =
                new GetCurrentUserDepartmentRolesQuery(null, "DEPT1");

        when(authenticationHelper.getSub()).thenReturn("user-ext-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("user-ext-1"))
                .thenReturn(Optional.of(user));
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT1"))
                .thenReturn(department);

        when(roleRepository.findByDepartmentIdAndUserIdAndStatusNotDeleted(user, department))
                .thenReturn(List.of());

        ResponseEntity<List<RoleDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertTrue(response.getBody().isEmpty());
    }

    // ---------------------------------------------------------------------
    // 3. ERROR CASE — User not found → throws UNAUTHORIZED
    // ---------------------------------------------------------------------
    @Test
    void handle_userNotFound_throwsUnauthorized() {

        when(authenticationHelper.getSub()).thenReturn("unknown-ext");
        when(userRepository.findByExternalIdWithRolesAndPermissions("unknown-ext"))
                .thenReturn(Optional.empty());

        GetCurrentUserDepartmentRolesQuery query =
                new GetCurrentUserDepartmentRolesQuery(null, "DEPT1");

        assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(query));

        verify(roleRepository, never()).findByDepartmentIdAndUserIdAndStatusNotDeleted(any(), any());
    }

    // ---------------------------------------------------------------------
    // 4. SUCCESS CASE — Department found but repository returns empty roles
    // ---------------------------------------------------------------------
    @Test
    void handle_success_departmentFoundButNoRoles() {

        GetCurrentUserDepartmentRolesQuery query =
                new GetCurrentUserDepartmentRolesQuery(null, "DEPT1");

        when(authenticationHelper.getSub()).thenReturn("user-ext-1");
        when(userRepository.findByExternalIdWithRolesAndPermissions("user-ext-1"))
                .thenReturn(Optional.of(user));
        when(departmentRepository.findByCodeAndStatusNotDeleted("DEPT1"))
                .thenReturn(department);

        when(roleRepository.findByDepartmentIdAndUserIdAndStatusNotDeleted(user, department))
                .thenReturn(List.of());

        ResponseEntity<List<RoleDTO>> response = handler.handle(query);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}