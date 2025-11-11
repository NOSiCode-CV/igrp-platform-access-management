package cv.igrp.platform.access_management.permission.application.commands;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.DepartmentEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.DepartmentEntityRepository;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpdatePermissionCommandHandlerTest {

    @InjectMocks
    private UpdatePermissionCommandHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private DepartmentEntityRepository departmentRepository;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Captor
    private ArgumentCaptor<PermissionEntity> permissionCaptor;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFound_When_ProvidedPermissionId_NotFound() {
        //... Given
        int permissionId = 100;
        String permissionName = "READ_DATA";
        String permissionNewDescription = "PermissionNewId";

        PermissionDTO permissionNewData = new PermissionDTO();

        permissionNewData.setDescription(permissionNewDescription);
        permissionNewData.setStatus(Status.INACTIVE);
        UpdatePermissionCommand command = new UpdatePermissionCommand(permissionNewData, permissionName);

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(command));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
    }

    @Test
    void itShouldUpdatePermission_WhenValidCommandIsProvided() {
        // Given
        int permissionId = 1;
        int departmentId = 100;
        String permissionName = "READ_DATA";
        String departmentCode = "DEPT";
        String permissionPreviousDescription = "Old Description";
        String permissionNewDescription = "New Description";

        DepartmentEntity department = new DepartmentEntity();
        department.setId(departmentId);
        department.setCode(departmentCode);

        PermissionEntity existingPermission = new PermissionEntity();
        existingPermission.setId(permissionId);
        existingPermission.setName(permissionName);
        existingPermission.setDescription(permissionPreviousDescription);
        existingPermission.setStatus(Status.INACTIVE);
        existingPermission.setDepartment(department);

        PermissionDTO dto = new PermissionDTO();
        dto.setDescription(permissionNewDescription);
        dto.setName(permissionName);
        dto.setStatus(Status.ACTIVE);
        dto.setDepartmentCode(departmentCode);

        UpdatePermissionCommand command = new UpdatePermissionCommand(dto, permissionName);

        PermissionEntity updatedPermission = new PermissionEntity();
        PermissionDTO mappedDTO = new PermissionDTO();

        // When
        when(permissionRepository.findByNameAndStatusNot(permissionName, Status.DELETED)).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.save(any(PermissionEntity.class))).thenReturn(updatedPermission);
        when(permissionMapper.mapToDTO(updatedPermission)).thenReturn(mappedDTO);

        ResponseEntity<PermissionDTO> response = underTest.handle(command);

        // Then
        verify(permissionRepository).save(permissionCaptor.capture());
        PermissionEntity savedPermission = permissionCaptor.getValue();

        assertEquals(permissionNewDescription, savedPermission.getDescription());
        assertEquals(Status.ACTIVE, savedPermission.getStatus());
        assertEquals(departmentCode, savedPermission.getDepartment().getCode());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mappedDTO, response.getBody());
    }
}