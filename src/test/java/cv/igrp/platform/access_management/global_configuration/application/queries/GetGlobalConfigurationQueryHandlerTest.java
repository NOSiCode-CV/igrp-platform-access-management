package cv.igrp.platform.access_management.global_configuration.application.queries;

import cv.igrp.platform.access_management.global_configuration.application.constants.GlobalConfigurationType;
import cv.igrp.platform.access_management.global_configuration.application.dto.GlobalConfigurationDTO;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.entity.GlobalConfigurationEntity;
import cv.igrp.platform.access_management.global_configuration.infrastructure.persistence.repository.GlobalConfigurationEntityRepository;
import cv.igrp.platform.access_management.global_configuration.mapper.GlobalConfigurationMapper;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetGlobalConfigurationQueryHandlerTest {

  @Mock
  private GlobalConfigurationEntityRepository repository;

  @Mock
  private GlobalConfigurationMapper mapper;

  @InjectMocks
  private GetGlobalConfigurationQueryHandler handler;

  private GetGlobalConfigurationQuery query;

  @BeforeEach
  void setUp() {
    query = new GetGlobalConfigurationQuery("CLUSTER"); // example type
  }

  @Test
  void testHandle_WhenConfigurationFound_ShouldReturnOkResponse() {
    try (MockedStatic<GlobalConfigurationType> mockedStatic = mockStatic(GlobalConfigurationType.class)) {
      // Given
      GlobalConfigurationType type = GlobalConfigurationType.CLUSTER;
      GlobalConfigurationEntity entity = new GlobalConfigurationEntity();
      GlobalConfigurationDTO dto = new GlobalConfigurationDTO();

      mockedStatic.when(() -> GlobalConfigurationType.fromCode("CLUSTER"))
              .thenReturn(Optional.of(type));

      List<GlobalConfigurationEntity> entities = new ArrayList<>();

      entities.add(entity);

      when(repository.findByTypeOrderByLastModifiedDateDesc(any()))
              .thenReturn(entities);

      when(mapper.toDto(entity)).thenReturn(dto);

      // When
      ResponseEntity<GlobalConfigurationDTO> response = handler.handle(query);

      // Then
      assertNotNull(response);
      assertEquals(HttpStatus.OK, response.getStatusCode());
      assertEquals(dto, response.getBody());
      verify(repository).findByTypeOrderByLastModifiedDateDesc(any());
      verify(mapper).toDto(entity);
    }
  }

  @Test
  void testHandle_WhenTypeNotFound_ShouldThrowBadRequest() {
    try (MockedStatic<GlobalConfigurationType> mockedStatic = mockStatic(GlobalConfigurationType.class)) {
      // Given
      mockedStatic.when(() -> GlobalConfigurationType.fromCode("INVALID"))
              .thenReturn(Optional.empty());

      GetGlobalConfigurationQuery invalidQuery = new GetGlobalConfigurationQuery("INVALID");

      // When / Then
      IgrpResponseStatusException ex = assertThrows(
              IgrpResponseStatusException.class,
              () -> handler.handle(invalidQuery)
      );

      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
  }

  @Test
  void testHandle_WhenConfigurationListEmpty_ShouldThrowNotFound() {
    try (MockedStatic<GlobalConfigurationType> mockedStatic = mockStatic(GlobalConfigurationType.class)) {
      // Given
      GlobalConfigurationType type = GlobalConfigurationType.CLUSTER;

      mockedStatic.when(() -> GlobalConfigurationType.fromCode("CLUSTER"))
              .thenReturn(Optional.of(type));

      when(repository.findByTypeOrderByLastModifiedDateDesc(any()))
              .thenReturn(new ArrayList<>());

      // When / Then
      IgrpResponseStatusException ex = assertThrows(
              IgrpResponseStatusException.class,
              () -> handler.handle(query)
      );

      assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
  }
}