package cv.igrp.platform.access_management.files.application.queries;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import cv.igrp.platform.access_management.files.application.dto.FileUrlDTO;
import cv.igrp.platform.access_management.shared.domain.exceptions.IgrpResponseStatusException;
import cv.igrp.platform.filemanager.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import cv.igrp.platform.access_management.files.application.queries.*;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
public class GetPrivateFileUrlQueryHandlerTest {

  @InjectMocks
  private GetPrivateFileUrlQueryHandler getPrivateFileUrlQueryHandler;

  @Mock
  private StorageService fileManagerService;

    @BeforeEach
  void setUp() {
    getPrivateFileUrlQueryHandler = new GetPrivateFileUrlQueryHandler(fileManagerService);
  }

  @Test
  void shouldThrowExceptionWhenPathIsBlank() {
    GetPrivateFileUrlQuery query = new GetPrivateFileUrlQuery(" ");

    IgrpResponseStatusException exception = assertThrows(
            IgrpResponseStatusException.class,
            () -> getPrivateFileUrlQueryHandler.handle(query)
    );
    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("No path provided", exception.getBody().getTitle());
    assertEquals("There's no path provided. Please check and try again.", exception.getBody().getProperties().get("details"));
  }

  @Test
  void shouldGenerateFileUrlSuccessfully() {
    // Arrange
    String filePath = "private/folder/user/123_file.pdf";
    String expectedUrl = "http://localhost:9000/" + filePath;
    int testExpirationTime = 3600;
    when(fileManagerService.getFileUrl(filePath)).thenReturn(expectedUrl);

    GetPrivateFileUrlQuery query = new GetPrivateFileUrlQuery(filePath);

    getPrivateFileUrlQueryHandler.setUrlExpirationTimeInSeconds(testExpirationTime);
    // Act
    ResponseEntity<FileUrlDTO> response = getPrivateFileUrlQueryHandler.handle(query);

    // Assert
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());

    FileUrlDTO dto = response.getBody();
    assertNotNull(dto);
    assertEquals(expectedUrl, dto.getUrl());

    LocalDateTime expiration = LocalDateTime.parse(dto.getExpiration());

    LocalDateTime expectedMin = LocalDateTime.now().plusSeconds(testExpirationTime - 1);
    LocalDateTime expectedMax = LocalDateTime.now().plusSeconds(testExpirationTime + 1);

    assertTrue(expiration.isAfter(expectedMin) && expiration.isBefore(expectedMax));

    verify(fileManagerService).getFileUrl(filePath);
  }

  @Test
  void shouldThrowExceptionWhenPathIsNull() {
    // Arrange
    GetPrivateFileUrlQuery query = new GetPrivateFileUrlQuery(null);

    // Act & Assert
    IgrpResponseStatusException exception = assertThrows(
            IgrpResponseStatusException.class,
            () -> getPrivateFileUrlQueryHandler.handle(query)
    );

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("No path provided", exception.getBody().getTitle());
  }

  @Test
  void shouldUseCorrectDateTimeFormat() {
    // Arrange
    String filePath = "private/docs/test/456_file.txt";
    when(fileManagerService.getFileUrl(any())).thenReturn("any-url");

    GetPrivateFileUrlQuery query = new GetPrivateFileUrlQuery(filePath);

    // Act
    ResponseEntity<FileUrlDTO> response = getPrivateFileUrlQueryHandler.handle(query);

    // Assert
    String expiration = response.getBody().getExpiration();
    assertDoesNotThrow(() -> LocalDateTime.parse(expiration));
  }


}