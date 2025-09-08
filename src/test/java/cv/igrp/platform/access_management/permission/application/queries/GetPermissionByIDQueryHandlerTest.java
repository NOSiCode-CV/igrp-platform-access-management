package cv.igrp.platform.access_management.permission.application.queries;

import cv.igrp.platform.access_management.permission.domain.service.PermissionMapper;
import cv.igrp.platform.access_management.shared.application.constants.Status;
import cv.igrp.platform.access_management.shared.application.dto.PermissionDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.PermissionEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.PermissionEntityRepository;
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
public class GetPermissionByIDQueryHandlerTest {

    @InjectMocks
    private GetPermissionByIDQueryHandler underTest;
    @Mock
    private PermissionEntityRepository permissionRepository;
    @Mock
    private PermissionMapper permissionMapper;

    @Test
    void itShouldStartContext() {
        assertNotNull(underTest);
    }

    @Test
    void itShouldThrowRecordNotFoundException_WhenProvidedId_DoesNotExist() {
        //... Given
        int permissionId = 100;
        GetPermissionByIDQuery query = new GetPermissionByIDQuery(permissionId);

        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED))
                .thenReturn(Optional.empty());

        //... When
        IgrpResponseStatusException response = assertThrows(IgrpResponseStatusException.class, () -> underTest.handle(query));

        //... Then
        assertEquals(HttpStatus.NOT_FOUND.value(), response.getBody().getStatus());
        verifyNoInteractions(permissionMapper);
    }

    @Test
    void itShouldReturnPermissionDTO_WhenPermissionExists() {
        // Given
        int permissionId = 10;
        GetPermissionByIDQuery query = new GetPermissionByIDQuery(permissionId);

        PermissionEntity permission = new PermissionEntity();
        permission.setId(permissionId);
        permission.setName("READ_USERS");
        permission.setStatus(Status.ACTIVE);

        PermissionDTO expectedDTO = new PermissionDTO();
        expectedDTO.setId(permissionId);
        expectedDTO.setName("READ_USERS");
        expectedDTO.setStatus(Status.ACTIVE);

        // Stubbing
        when(permissionRepository.findByIdAndStatusNot(permissionId, Status.DELETED))
                .thenReturn(Optional.of(permission));
        when(permissionMapper.mapToDTO(permission)).thenReturn(expectedDTO);

        // When
        ResponseEntity<PermissionDTO> response = underTest.handle(query);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedDTO, response.getBody());

        verify(permissionRepository).findByIdAndStatusNot(permissionId, Status.DELETED);
        verify(permissionMapper).mapToDTO(permission);
    }
}