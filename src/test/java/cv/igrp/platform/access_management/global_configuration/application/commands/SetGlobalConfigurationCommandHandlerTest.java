package cv.igrp.platform.access_management.global_configuration.application.commands;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.entity.GlobalConfigurationEntity;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository.GlobalConfigurationEntityRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test for {@link SetGlobalConfigurationCommandHandler}.
 */
@ExtendWith(MockitoExtension.class)
class SetGlobalConfigurationCommandHandlerTest {

    @Mock
    private GlobalConfigurationEntityRepository repository;

    @Mock
    private GlobalConfigurationMapper mapper;

    @InjectMocks
    private SetGlobalConfigurationCommandHandler underTest;

    private GlobalConfigurationDTO inputDto;
    private GlobalConfigurationEntity entity;
    private SetGlobalConfigurationCommand command;

    @BeforeEach
    void setUp() {
        inputDto = new GlobalConfigurationDTO();
        inputDto.setConfig("{\"app.name\": \"iGRP\"}");
        inputDto.setType(GlobalConfigurationType.CLUSTER);

        entity = new GlobalConfigurationEntity();
        entity.setId(1);
        entity.setConfig("{\"app.name\": \"iGRP\"}");
        entity.setType(GlobalConfigurationType.CLUSTER);

        command = new SetGlobalConfigurationCommand(inputDto);
    }

    /**
     * Test that handle() correctly saves and returns a GlobalConfigurationDTO.
     */
    @Test
    void itShouldSaveAndReturnGlobalConfigurationSuccessfully() {
        // Given
        when(mapper.toEntity(inputDto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(inputDto);

        // When
        ResponseEntity<GlobalConfigurationDTO> response = underTest.handle(command);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("{\"app.name\": \"iGRP\"}", response.getBody().getConfig());
        assertEquals(GlobalConfigurationType.CLUSTER, response.getBody().getType());

        verify(mapper).toEntity(inputDto);
        verify(repository).save(entity);
        verify(mapper).toDto(entity);
    }

    /**
     * Test that handle() works even with minimal configuration data.
     */
    @Test
    void itShouldHandleEmptyConfigurationGracefully() {
        // Given
        GlobalConfigurationDTO emptyDto = new GlobalConfigurationDTO();
        emptyDto.setConfig("{}");
        emptyDto.setType(GlobalConfigurationType.ORGANIZATION);

        GlobalConfigurationEntity emptyEntity = new GlobalConfigurationEntity();
        emptyEntity.setConfig("{}");
        emptyEntity.setType(GlobalConfigurationType.ORGANIZATION);

        SetGlobalConfigurationCommand emptyCommand = new SetGlobalConfigurationCommand(emptyDto);

        when(mapper.toEntity(emptyDto)).thenReturn(emptyEntity);
        when(repository.save(emptyEntity)).thenReturn(emptyEntity);
        when(mapper.toDto(emptyEntity)).thenReturn(emptyDto);

        // When
        ResponseEntity<GlobalConfigurationDTO> response = underTest.handle(emptyCommand);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GlobalConfigurationType.ORGANIZATION, response.getBody().getType());

        verify(mapper).toEntity(emptyDto);
        verify(repository).save(emptyEntity);
        verify(mapper).toDto(emptyEntity);
    }
}
