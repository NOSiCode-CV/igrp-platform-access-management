package cv.igrp.platform.access_management.resource.application.commands;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.resource.mapper.ResourceMapper;
import cv.igrp.platform.access_management.shared.application.constants.ResourceType;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.entity.ResourceEntity;
import cv.igrp.platform.access_management.shared.infrastructure.persistence.repository.ResourceEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.resource.application.dto.*;

import java.util.Objects;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdateResourceCommandHandler Test Suite")
public class UpdateResourceCommandHandlerTest {

    @Mock
    private ResourceEntityRepository resourceRepository;

    @Mock
    private ResourceMapper resourceMapper;

    @InjectMocks
    private UpdateResourceCommandHandler handler;

    private UpdateResourceCommand updateResourceCommand(ResourceDTO resourcedto,
                                                        Integer resourceId){
        return new UpdateResourceCommand(resourcedto, resourceId);
    }

    private ResourceDTO dto;
    private ResourceEntity resource;
    private ResourceEntity updatedResource;
    private UpdateResourceCommand command;

    @BeforeEach
    void setUp() {
        dto = new ResourceDTO();
        dto.setName("Updated Name");
        dto.setType(ResourceType.API);

        resource = new ResourceEntity();
        resource.setId(1);
        resource.setName("Old Name");
        resource.setType(ResourceType.UI);

        updatedResource = new ResourceEntity();
        updatedResource.setId(1);
        updatedResource.setName("Updated Name");
        updatedResource.setType(ResourceType.API);
    }


    @Test
    @DisplayName("Should update resource when valid ID and data are provided")
    void testHandle_shouldUpdateResourceSuccessfully() {
        // Arrange
        command = updateResourceCommand(dto, 1);

        when(resourceRepository.findById(1)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(resource)).thenReturn(updatedResource);
        when(resourceMapper.toDto(updatedResource)).thenReturn(dto);

        // Act
        ResponseEntity<ResourceDTO> response = handler.handle(command);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Updated Name", Objects.requireNonNull(response.getBody()).getName());
        assertEquals(ResourceType.API, Objects.requireNonNull(response.getBody()).getType());

        // Verify
        verify(resourceRepository, times(1)).findById(1);
        verify(resourceRepository, times(1)).save(resource);
        verify(resourceMapper,times(1)).toDto(updatedResource);
        verifyNoMoreInteractions(resourceMapper,resourceRepository);
    }

    @Test
    @DisplayName("Should throw exception when resource ID does not exist")
    void testHandle_whenResourceNotFound_shouldThrowException() {
        // Arrange
        command = updateResourceCommand(dto, 99);

        when(resourceRepository.findById(99)).thenReturn(Optional.empty());

        // Act
        IgrpResponseStatusException exception = assertThrows(IgrpResponseStatusException.class,
                () -> handler.handle(command));

        // Assert
        assertEquals(HttpStatus.NOT_FOUND.value(), exception.getBody().getStatus());

        assertNotNull(exception.getBody().getProperties());
        assertTrue(exception.getBody().getProperties().getOrDefault("details", "").toString().contains("Resource not found"));

        // Verify
        verify(resourceRepository, times(1)).findById(99);
        verifyNoMoreInteractions(resourceRepository);
    }

}